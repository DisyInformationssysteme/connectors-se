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
package org.talend.components.jdbc.output;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.platforms.DatabaseSpecial;
import org.talend.components.jdbc.platforms.ErrorFactory;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.platforms.RuntimeEnvUtil;
import org.talend.components.jdbc.platforms.cloud.QueryManager;
import org.talend.components.jdbc.platforms.cloud.QueryManagerFactory;
import org.talend.components.jdbc.platforms.cloud.Reject;
import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.ReturnVariables.ReturnVariable;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.context.RuntimeContext;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.connection.Connection;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;

import static org.talend.sdk.component.api.component.ReturnVariables.ReturnVariable.AVAILABILITY.AFTER;

@Slf4j
@Getter
@Version(1)
@ReturnVariable(value = "NB_LINE_INSERTED", availability = AFTER, type = Integer.class)
@ReturnVariable(value = "NB_LINE_UPDATED", availability = AFTER, type = Integer.class)
@ReturnVariable(value = "NB_LINE_DELETED", availability = AFTER, type = Integer.class)
@ReturnVariable(value = "NB_LINE_REJECTED", availability = AFTER, type = Integer.class)
@ReturnVariable(value = "QUERY", availability = AFTER, type = String.class)
@Processor(name = "Output")
@Icon(value = Icon.IconType.CUSTOM, custom = "JDBCOutput")
@Documentation("JDBC Output component")
public class OutputProcessor implements Serializable {

    private static final long serialVersionUID = 1;

    private final JDBCOutputConfig configuration;

    private final RecordBuilderFactory recordBuilderFactory;

    private final JDBCService jdbcService;

    // private final I18nMessage i18n;

    @RuntimeContext
    private transient RuntimeContextHolder context;

    @Connection
    private transient java.sql.Connection connection;

    private transient JDBCService.DataSourceWrapper dataSource;

    private transient boolean init;

    private transient JDBCOutputWriter writer;

    private transient Boolean tableExistsCheck;

    private transient boolean tableCreated;

    private transient String driverId;

    private transient Platform platform;

    private transient QueryManager queryManager;

    private transient List<Record> records;

    private transient int batchSize;

    private transient int batchCount;

    public OutputProcessor(@Option("configuration") final JDBCOutputConfig configuration,
            final JDBCService jdbcService, final RecordBuilderFactory recordBuilderFactory/*
                                                                                           * , final I18nMessage
                                                                                           * i18nMessage
                                                                                           */) {
        this.configuration = configuration;
        this.jdbcService = jdbcService;
        this.recordBuilderFactory = recordBuilderFactory;
        this.batchSize = this.configuration.getBatchSize();// batch size will always show for cloud
        // this.i18n = i18nMessage;
    }

    @ElementListener
    public void elementListener(@Input final Record record, @Output final OutputEmitter<Record> success,
            @Output("reject") final OutputEmitter<Record> reject)
            throws SQLException {
        if (!init) {
            boolean useExistedConnection = false;

            final boolean isCloud = RuntimeEnvUtil.isCloud(configuration.getDataSet().getDataStore());

            if (connection == null) {
                try {
                    Map<String, String> additionalJDBCProperties = new HashMap<>();
                    if (isCloud && configuration.isRewriteBatchedStatements()) {
                        additionalJDBCProperties.put("rewriteBatchedStatements", "true");
                    }
                    dataSource = jdbcService.createConnectionOrGetFromSharedConnectionPoolOrDataSource(
                            configuration.getDataSet().getDataStore(), context, false, additionalJDBCProperties);

                    if (configuration.getCommitEvery() != 0) {
                        dataSource.getConnection().setAutoCommit(false);
                    }
                } catch (SQLException e) {
                    log.warn(e.getMessage());
                }
            } else {
                useExistedConnection = true;
                dataSource = new JDBCService.DataSourceWrapper(null, connection);
            }

            if (isCloud) {
                this.driverId =
                        jdbcService.getPlatformService().getDriver(configuration.getDataSet().getDataStore()).getId();
                this.platform =
                        jdbcService.getPlatformService().getPlatform(configuration.getDataSet().getDataStore());
                this.queryManager = QueryManagerFactory.getQueryManager(platform, jdbcService.getI18n(), configuration,
                        recordBuilderFactory);
            }

            if (queryManager == null) {
                switch (configuration.getDataAction()) {
                case INSERT:
                    writer = new JDBCOutputInsertWriter(configuration, jdbcService, useExistedConnection, dataSource,
                            recordBuilderFactory, context);
                    break;
                case UPDATE:
                    writer = new JDBCOutputUpdateWriter(configuration, jdbcService, useExistedConnection, dataSource,
                            recordBuilderFactory, context);
                    break;
                case INSERT_OR_UPDATE:
                    writer = new JDBCOutputInsertOrUpdateWriter(configuration, jdbcService, useExistedConnection,
                            dataSource,
                            recordBuilderFactory, context);
                    break;
                case UPDATE_OR_INSERT:
                    writer = new JDBCOutputUpdateOrInsertWriter(configuration, jdbcService, useExistedConnection,
                            dataSource,
                            recordBuilderFactory, context);
                    break;
                case DELETE:
                    writer = new JDBCOutputDeleteWriter(configuration, jdbcService, useExistedConnection, dataSource,
                            recordBuilderFactory, context);
                    break;
                }

                writer.open();
            } else {
                records = new ArrayList<>(1000);
            }

            init = true;
        }

        if (queryManager != null) {
            batchCount++;
            records.add(record);
            if (batchCount < batchSize) {

            } else {
                batchCount = 0;
                try {
                    createTableIfNeed();
                    final List<Reject> discards = queryManager.execute(records, dataSource);
                    records = new ArrayList<>(1000);
                    discards.stream().map(Object::toString).forEach(log::error);
                } catch (final SQLException | IOException e) {
                    records.stream().map(r -> new Reject(e.getMessage(), r)).map(Reject::toString).forEach(log::error);
                    throw ErrorFactory.toIllegalStateException(e);
                }
            }
            return;
        }

        // as output component, it's impossible that record is null
        if (record == null) {
            return;
        }

        writer.write(record);

        List<Record> successfulWrites = writer.getSuccessfulWrites();
        for (Record r : successfulWrites) {
            success.emit(r);
        }

        List<Record> rejectedWrites = writer.getRejectedWrites();
        for (Record r : rejectedWrites) {
            reject.emit(r);
        }
    }

    private void createTableIfNeed() throws SQLException {
        if (this.tableExistsCheck == null) {
            this.tableExistsCheck = DatabaseSpecial.checkTableExistence(driverId,
                    configuration.getDataSet().getTableName(), dataSource);
        }

        if (!this.tableExistsCheck && !this.configuration.isCreateTableIfNotExists()) {
            throw new IllegalStateException(
                    jdbcService.getI18n()
                            .errorTaberDoesNotExists(this.configuration.getDataSet().getTableName()));
        }

        if (!tableExistsCheck && !tableCreated && configuration.isCreateTableIfNotExists()) {
            // use the connector nested mapping file
            final Dbms mapping =
                    CommonUtils.getMapping("/mappings", configuration.getDataSet().getDataStore(), null,
                            null, jdbcService);

            // no need to close the connection as expected reuse for studio and get it from pool for cloud
            final java.sql.Connection connection = dataSource.getConnection();
            platform.createTableIfNotExist(connection, records, mapping, configuration, recordBuilderFactory);
            tableCreated = true;
        }
    }

    @PostConstruct
    public void init() {
    }

    @PreDestroy
    public void release() throws SQLException {
        if (queryManager != null && dataSource != null) {
            if (batchCount > 0) {
                batchCount = 0;
                try {
                    createTableIfNeed();
                    final List<Reject> discards = queryManager.execute(records, dataSource);
                    discards.stream().map(Object::toString).forEach(log::error);
                } catch (final SQLException | IOException e) {
                    records.stream().map(r -> new Reject(e.getMessage(), r)).map(Reject::toString).forEach(log::error);
                    throw ErrorFactory.toIllegalStateException(e);
                }
            }
            dataSource.close();
        }

        if (records != null) {
            records = null;
        }

        if (writer != null) {
            writer.close();
        }
    }

}
