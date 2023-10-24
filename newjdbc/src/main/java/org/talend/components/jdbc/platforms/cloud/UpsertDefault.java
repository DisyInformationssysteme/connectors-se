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
import org.talend.components.jdbc.output.JDBCSQLBuilder;
import org.talend.components.jdbc.output.RowWriter;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Slf4j
@Getter
public class UpsertDefault extends QueryManagerImpl {

    private final Insert insert;

    private final Update update;

    private final List<String> keys;

    private final List<String> ignoreColumns;

    public UpsertDefault(final Platform platform, final JDBCOutputConfig configuration, final I18nMessage i18n,
            final RecordBuilderFactory recordBuilderFactory) {
        super(platform, configuration, i18n, recordBuilderFactory);
        this.keys = new ArrayList<>(ofNullable(configuration.getKeys()).orElse(emptyList()));
        this.ignoreColumns = new ArrayList<>(ofNullable(configuration.getIgnoreUpdate()).orElse(emptyList()));
        insert = new Insert(platform, configuration, i18n, recordBuilderFactory);
        update = new Update(platform, configuration, i18n, recordBuilderFactory);
    }

    @Override
    public PreparedStatement buildQuery(final List<Record> records, final Connection connection) throws SQLException {
        final List<Schema.Entry> entries = records
                .stream()
                .flatMap(r -> r.getSchema().getEntries().stream())
                .distinct()
                .collect(toList());

        final Schema.Builder schemaBuilder = getRecordBuilderFactory().newSchemaBuilder(Schema.Type.RECORD);
        entries.stream().forEach(entry -> schemaBuilder.withEntry(entry));
        final Schema inputSchema = schemaBuilder.build();

        final Schema currentSchema = SchemaInferer.mergeRuntimeSchemaAndDesignSchema4Dynamic(
                getConfiguration().getDataSet().getSchema(), inputSchema, getRecordBuilderFactory());

        final List<JDBCSQLBuilder.Column> columnList = JDBCSQLBuilder.getInstance()
                .createColumnList(getConfiguration(), currentSchema, getConfiguration().isUseOriginColumnName(), keys,
                        null);

        final String sql = JDBCSQLBuilder.getInstance()
                .generateQuerySQL4InsertOrUpdate(getPlatform(), getConfiguration().getDataSet().getTableName(),
                        columnList);

        final PreparedStatement statement = connection.prepareStatement(sql);

        final List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
        for (JDBCSQLBuilder.Column column : columnList) {
            if (column.addCol || (column.isReplaced())) {
                continue;
            }

            if (column.updateKey) {
                columnList4Statement.add(column);
            }
        }

        rowWriter = new RowWriter(columnList4Statement, inputSchema, currentSchema, statement);

        return statement;
    }

    @Override
    public List<Reject> execute(final List<Record> records, final JDBCService.DataSourceWrapper dataSource)
            throws SQLException {
        if (records.isEmpty()) {
            return emptyList();
        }
        final List<Record> needUpdate = new ArrayList<>();
        final List<Record> needInsert = new ArrayList<>();
        final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = buildQuery(records, connection);
        final List<Reject> discards = new ArrayList<>();
        try {
            for (final Record record : records) {
                statement.clearParameters();

                String sql_fact = rowWriter.write(record);
                if (getConfiguration().isDebugQuery() && sql_fact != null) {
                    log.debug("'" + sql_fact.trim() + "'.");
                }

                try (final ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getInt(1) > 0) {
                        needUpdate.add(record);
                    } else {
                        needInsert.add(record);
                    }
                }
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (final SQLException e) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            throw e;
        } finally {
            statement.close();
        }

        // fixme handle the update and insert in // need a pool of 2 !
        if (!needInsert.isEmpty()) {
            discards.addAll(insert.execute(needInsert, dataSource));
        }
        if (!needUpdate.isEmpty()) {
            discards.addAll(update.execute(needUpdate, dataSource));
        }

        return discards;
    }
}
