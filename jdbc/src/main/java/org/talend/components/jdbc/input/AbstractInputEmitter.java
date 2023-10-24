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
package org.talend.components.jdbc.input;

import static java.util.Locale.ROOT;
import static org.talend.components.jdbc.ErrorFactory.toIllegalStateException;

import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.jooq.impl.ParserException;
import org.talend.components.jdbc.configuration.InputConfig;
import org.talend.components.jdbc.dataset.BaseDataSet;
import org.talend.components.jdbc.output.platforms.MariaDbPlatform;
import org.talend.components.jdbc.output.platforms.Platform;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.components.jdbc.service.JdbcService;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractInputEmitter implements Serializable {

    private final InputConfig inputConfig;

    private final RecordBuilderFactory recordBuilderFactory;

    private final JdbcService jdbcDriversService;

    private final I18nMessage i18n;

    protected Connection connection;

    private Statement statement;

    private ResultSet resultSet;

    private JdbcService.JdbcDatasource dataSource;

    private transient Schema schema;

    AbstractInputEmitter(final InputConfig inputConfig, final JdbcService jdbcDriversService,
            final RecordBuilderFactory recordBuilderFactory, final I18nMessage i18nMessage) {
        this.inputConfig = inputConfig;
        this.recordBuilderFactory = recordBuilderFactory;
        this.jdbcDriversService = jdbcDriversService;
        this.i18n = i18nMessage;
    }

    @PostConstruct
    public void init() {
        final BaseDataSet dataSet = inputConfig.getDataSet();
        final Platform platform = jdbcDriversService.getPlatformService().getPlatform(dataSet.getConnection());
        final String query = dataSet.getQuery(platform);
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException(i18n.errorEmptyQuery());
        }
        try {
            if (jdbcDriversService.isInvalidSQLQuery(query, dataSet.getConnection().getDbType())) {
                throw new IllegalArgumentException(i18n.errorUnauthorizedQuery());
            }
        } catch (ParserException e) {
            throw new IllegalArgumentException(i18n.errorUnauthorizedQuery());
        }

        try {
            dataSource = jdbcDriversService.createDataSource(dataSet.getConnection());
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            statement.setFetchSize(dataSet.getFetchSize());
            resultSet = statement.executeQuery(query);

            schema = jdbcDriversService.createSchema(dataSet, connection, resultSet, recordBuilderFactory);

            log.debug("Input schema: {}", schema);
            if (log.isDebugEnabled()) {
                log.debug("SchemaRaw: {}",
                        schema.getEntries().stream().map(Schema.Entry::getRawName).collect(Collectors.joining(",")));
            }
        } catch (final SQLException e) {
            throw toIllegalStateException(e);
        }
    }

    @Producer
    public Record next() {
        try {
            if (!resultSet.next()) {
                return null;
            }
            final Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schema);
            final List<Schema.Entry> columns = Optional.ofNullable(schema.getEntries()).orElse(Collections.emptyList());
            final String dbType = inputConfig.getDataSet().getConnection().getDbType().toLowerCase(ROOT);
            for (int index = 0; index < columns.size(); index++) {
                if (MariaDbPlatform.MARIADB.equals(dbType) && "BYTES".equals(columns.get(index).getType().name())) {
                    jdbcDriversService.addColumn(recordBuilder, columns.get(index), resultSet.getBytes(index + 1));
                } else {
                    jdbcDriversService.addColumn(recordBuilder, columns.get(index), resultSet.getObject(index + 1));
                }
            }
            return recordBuilder.build();
        } catch (final SQLException e) {
            throw toIllegalStateException(e);
        }
    }

    @PreDestroy
    public void release() {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.warn(i18n.warnResultSetCantBeClosed(), e);
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                log.warn(i18n.warnStatementCantBeClosed(), e);
            }
        }
        if (connection != null) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
            } catch (final SQLException e) {
                log.error(i18n.errorSQL(e.getErrorCode(), e.getMessage()), e);
                try {
                    if (!connection.getAutoCommit()) {
                        connection.rollback();
                    }
                } catch (final SQLException rollbackError) {
                    log.error(i18n.errorSQL(rollbackError.getErrorCode(), rollbackError.getMessage()), rollbackError);
                }
            }
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn(i18n.warnConnectionCantBeClosed(), e);
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

}
