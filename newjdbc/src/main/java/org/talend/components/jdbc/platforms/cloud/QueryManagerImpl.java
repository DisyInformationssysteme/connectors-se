/*
 * Copyright (C) 2006-2023 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.jdbc.platforms.cloud;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.output.RowWriter;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.*;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Optional.*;
import static java.util.stream.Collectors.toList;

@Slf4j
public abstract class QueryManagerImpl implements QueryManager {

    @Getter
    private final Platform platform;

    @Getter
    private final JDBCOutputConfig configuration;

    @Getter
    private final I18nMessage i18n;

    @Getter
    private final RecordBuilderFactory recordBuilderFactory;

    public QueryManagerImpl(final Platform platform, final JDBCOutputConfig configuration, final I18nMessage i18n,
            final RecordBuilderFactory recordBuilderFactory) {
        this.platform = platform;
        this.configuration = configuration;
        this.i18n = i18n;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    protected RowWriter rowWriter;

    private final Integer maxRetry = 10;

    private Integer retryCount = 0;

    protected PreparedStatement buildQuery(List<Record> records, Connection connection) throws SQLException {
        return null;
    };

    @Override
    public List<Reject> execute(final List<Record> records, final JDBCService.DataSourceWrapper dataSource)
            throws SQLException {
        if (records.isEmpty()) {
            return emptyList();
        }
        final Connection connection = dataSource.getConnection();
        return processRecords(records, connection, buildQuery(records, connection));
    }

    private List<Reject> processRecords(final List<Record> records, final Connection connection,
            final PreparedStatement statement)
            throws SQLException {
        List<Reject> rejects;
        do {
            rejects = new ArrayList<>();
            try {
                final Map<Integer, Integer> batchOrder = new HashMap<>();
                int recordIndex = -1;
                int batchNumber = -1;
                for (final Record record : records) {
                    recordIndex++;
                    statement.clearParameters();

                    String sql_fact = rowWriter.write(record);
                    if (configuration.isDebugQuery() && sql_fact != null) {
                        log.debug("'" + sql_fact.trim() + "'.");
                    }

                    statement.addBatch();
                    batchNumber++;
                    batchOrder.put(batchNumber, recordIndex);
                }

                try {
                    statement.executeBatch();
                    if (!connection.getAutoCommit()) {
                        connection.commit();
                    }
                    break;
                } catch (final SQLException e) {
                    if (!connection.getAutoCommit()) {
                        connection.rollback();
                    }
                    if (!retry(e) || retryCount > maxRetry) {
                        rejects.addAll(handleRejects(records, batchOrder, e));
                        break;
                    }
                    retryCount++;
                    log.warn("Deadlock detected. retrying for the " + retryCount + " time", e);
                    try {
                        Thread.sleep((long) Math.exp(retryCount) * 2000);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                statement.close();
            }
        } while (true);

        return rejects;
    }

    /**
     * A default retry strategy. We try to detect deadl lock by testing the sql state code.
     * 40001 is the state code used by almost all database to rise a dead lock issue
     */
    private boolean retry(final SQLException e) {
        return "40001".equals(ofNullable(e.getNextException()).orElse(e).getSQLState());
    }

    private List<Reject> handleRejects(final List<Record> records, Map<Integer, Integer> batchOrder,
            final SQLException e)
            throws SQLException {
        if (!(e instanceof BatchUpdateException)) {
            throw e;
        }
        final List<Reject> discards = new ArrayList<>();
        final int[] result = ((BatchUpdateException) e).getUpdateCounts();
        SQLException error = e;
        if (result.length == records.size()) {
            for (int i = 0; i < result.length; i++) {
                if (result[i] == Statement.EXECUTE_FAILED) {
                    error = ofNullable(error.getNextException()).orElse(error);
                    discards
                            .add(new Reject(error.getMessage(), error.getSQLState(), error.getErrorCode(),
                                    records.get(batchOrder.get(i))));
                }
            }
        } else {
            int failurePoint = result.length;
            error = ofNullable(error.getNextException()).orElse(error);
            discards
                    .add(new Reject(error.getMessage(), error.getSQLState(), error.getErrorCode(),
                            records.get(batchOrder.get(failurePoint))));
            // todo we may retry for this sub list
            discards
                    .addAll(records
                            .subList(batchOrder.get(failurePoint) + 1, records.size())
                            .stream()
                            .map(r -> new Reject("rejected due to error in previous elements error in this transaction",
                                    r))
                            .collect(toList()));
        }

        return discards;
    }

    public String namespace(final Connection connection) throws SQLException {
        String schenma = JDBCService.getDatabaseSchema(connection);
        return (connection.getCatalog() != null && !connection.getCatalog().isEmpty()
                ? getPlatform().identifier(connection.getCatalog()) + "."
                : "") + (schenma != null && !schenma.isEmpty() ? getPlatform().identifier(connection.getSchema()) : "");
    }

}
