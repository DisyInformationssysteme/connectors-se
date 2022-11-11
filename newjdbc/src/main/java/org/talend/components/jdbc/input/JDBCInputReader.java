/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
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

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.DBType;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.lang.reflect.Method;
import java.net.URL;
import java.sql.*;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * common JDBC reader
 */
@Slf4j
public class JDBCInputReader {

    protected JDBCInputConfig config;

    protected JDBCService.DataSourceWrapper conn;

    protected ResultSet resultSet;

    private RecordBuilderFactory recordBuilderFactory;

    private Schema querySchema;

    private Statement statement;

    private boolean useExistedConnection;

    private Record currentRecord;

    private long totalCount;

    private transient RuntimeContextHolder context;

    private final boolean trimAll;

    public JDBCInputReader(JDBCInputConfig config, boolean useExistedConnection, JDBCService.DataSourceWrapper conn,
            RecordBuilderFactory recordBuilderFactory, final RuntimeContextHolder context) {
        this.config = config;
        this.useExistedConnection = useExistedConnection;
        this.conn = conn;
        this.recordBuilderFactory = recordBuilderFactory;
        this.context = context;

        trimAll = config.isTrimAllStringOrCharColumns();

        if (!trimAll) {
            List<ColumnTrim> columnTrimList = config.getColumnTrims();
            // TODO now if studio design schema have dynamic type column, this fields will be empty as not pass
            // here must use SchemaInfo.label to search in columnTrimList, can't use entry name and origin db
            // name(right?)
            List<SchemaInfo> fields = config.getDataSet().getSchema();
        }
    }

    private Schema getSchema() throws SQLException {
        if (querySchema == null) {
            // TODO need to adjust studio common javajet as it don't pass value for "config.getDataSet().getSchema()" if
            // dynamic column exists in studio, even basic column also exist.
            // TODO solution 1: pass nondynamic columns, solution 2: pass all columns info which contain dynamic columns
            querySchema = SchemaInferer.convertSchemaInfoList2TckSchema(config.getDataSet().getSchema(),
                    recordBuilderFactory);

            // no set schema for cloud platform, or use dynamic in studio platform
            if (querySchema == null || querySchema.getEntries().isEmpty()) {
                URL mappingFileDir = null;
                if (context != null) {
                    // TODO set and init it in common javajet
                    Object value = context.getGlobal(CommonUtils.MAPPING_URL_SUBFIX);
                    if (value != null) {
                        mappingFileDir = URL.class.cast(value);
                    }
                }

                DBType dbTypeInComponentSetting = config.isEnableMapping() ? config.getMapping() : null;

                Dbms mapping = null;
                if (mappingFileDir != null) {
                    mapping = CommonUtils.getMapping(mappingFileDir, config.getDataSet().getDataStore(), null,
                            dbTypeInComponentSetting);
                } else {
                    // use the connector nested mapping file
                    mapping = CommonUtils.getMapping("/mappings", config.getDataSet().getDataStore(), null,
                            dbTypeInComponentSetting);
                }

                querySchema = SchemaInferer.infer(recordBuilderFactory, resultSet.getMetaData(), mapping);
            }

            boolean includeDynamic = false;// TODO process the case for dynamic column and basic column exists both,
                                           // need to merge runtime schema with design schema, not sure
            if (includeDynamic) {
                // querySchema = CommonUtils.mergeRuntimeSchema2DesignSchema4Dynamic(querySchema,
                // runtimeSchema4ResultSet);
            }
        }

        // this toString cost performance a lot
        // log.debug("QuerySchema: " + querySchema.toString());

        return querySchema;
    }

    public void open() throws SQLException {
        log.debug("JDBCInputReader start.");

        // TODO pass QUERY var

        boolean usePreparedStatement = config.isUsePreparedStatement();
        try {
            String driverClass = config.getDataSet().getDataStore().getJdbcClass();
            if (driverClass != null && driverClass.toLowerCase().contains("mysql")) {
                if (usePreparedStatement) {
                    log.debug("Prepared statement: " + config.getDataSet().getSqlQuery());
                    PreparedStatement prepared_statement = conn.getConnection()
                            .prepareStatement(config.getDataSet().getSqlQuery(),
                                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    JDBCRuntimeUtils.setPreparedStatement(prepared_statement, config.getPreparedStatementParameters());
                    statement = prepared_statement;
                } else {
                    log.debug("Create statement.");
                    statement = conn.getConnection()
                            .createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                }
                Class clazz = statement.getClass();
                try {
                    Method method = clazz.getMethod("enableStreamingResults");
                    if (method != null) {
                        // have to use reflect here
                        method.invoke(statement);
                    }
                } catch (Exception e) {
                    // TODO
                    // throw new RuntimeException(e);
                    log.info("can't find method : enableStreamingResults");
                }
            } else {
                if (usePreparedStatement) {
                    log.debug("Prepared statement: " + config.getDataSet().getSqlQuery());
                    PreparedStatement prepared_statement =
                            conn.getConnection().prepareStatement(config.getDataSet().getSqlQuery());
                    JDBCRuntimeUtils.setPreparedStatement(prepared_statement, config.getPreparedStatementParameters());
                    statement = prepared_statement;

                } else {
                    statement = conn.getConnection().createStatement();
                }
            }

            if (config.isUseQueryTimeout()) {
                log.debug("Query timeout: " + config.getQueryTimeout());
                statement.setQueryTimeout(config.getQueryTimeout());
            }

            if (config.isUseCursor()) {
                log.debug("Fetch size: " + config.getCursorSize());
                statement.setFetchSize(config.getCursorSize());
            }
            if (usePreparedStatement) {
                resultSet = ((PreparedStatement) statement).executeQuery();
            } else {
                log.debug("Executing the query: '{}'", config.getDataSet().getSqlQuery());
                resultSet = statement.executeQuery(config.getDataSet().getSqlQuery());
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean haveNext() throws SQLException {
        boolean haveNext = resultSet.next();

        if (haveNext) {
            totalCount++;
            log.debug("Retrieving the record: " + totalCount);

            final Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(getSchema());
            // final Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();// test prove this is low
            // performance

            SchemaInferer.fillValue(recordBuilder, getSchema(), resultSet);

            currentRecord = recordBuilder.build();
        }

        return haveNext;
    }

    public boolean advance() throws SQLException {
        try {
            return haveNext();
        } catch (SQLException e) {
            throw e;
        }
    }

    public Record getCurrent() throws NoSuchElementException {
        if (currentRecord == null) {
            throw new NoSuchElementException("start() wasn't called");
        }
        return currentRecord;
    }

    public void close() throws SQLException {
        try {
            if (resultSet != null) {
                resultSet.close();
                resultSet = null;
            }

            if (statement != null) {
                statement.close();
                statement = null;
            }

            if (!useExistedConnection && conn != null) {
                log.debug("Closing connection");
                conn.close();
                conn = null;
            }
        } catch (SQLException e) {
            throw e;
        }
    }

}
