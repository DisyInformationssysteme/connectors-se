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
package org.talend.components.jdbc.row;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.input.JDBCRuntimeUtils;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * the JDBC writer for JDBC row
 *
 */
@Slf4j
public class JDBCRowWriter {

    private JDBCService.DataSourceWrapper conn;

    private JDBCRowConfig config;

    private final List<Record> successfulWrites = new ArrayList<>();

    private final List<Record> rejectedWrites = new ArrayList<>();

    private int successCount;

    private int rejectCount;

    private boolean useExistedConnection;

    private boolean dieOnError;

    private PreparedStatement prepared_statement;

    private Statement statement;

    private ResultSet resultSet;

    private boolean usePreparedStatement;

    private String sql;

    private boolean propagateQueryResultSet;

    private boolean useCommit;

    private int commitCount;

    private int commitEvery;

    private Schema outSchema;

    private Schema rejectSchema;

    private Boolean useQueryTimeout;

    private Integer queryTimeout;

    protected final RecordBuilderFactory recordBuilderFactory;

    private int totalCount;

    private boolean detectErrorOnMultipleSQL;

    public JDBCRowWriter(JDBCRowConfig config, boolean useExistedConnection, JDBCService.DataSourceWrapper conn,
            RecordBuilderFactory recordBuilderFactory) {
        this.config = config;
        this.useExistedConnection = useExistedConnection;
        this.conn = conn;
        this.recordBuilderFactory = recordBuilderFactory;

        int commitEveryNumber = config.getCommitEvery();
        useCommit = !useExistedConnection && commitEveryNumber != 0;
        if (useCommit) {
            commitEvery = commitEveryNumber;
        }

        dieOnError = config.isDieOnError();

        propagateQueryResultSet = config.isPropagateRecordSet();

        outSchema =
                SchemaInferer.convertSchemaInfoList2TckSchema(config.getDataSet().getSchema(), recordBuilderFactory);
        rejectSchema = SchemaInferer.getRejectSchema(config.getDataSet().getSchema(), recordBuilderFactory);

        useQueryTimeout = config.isUseQueryTimeout();
        queryTimeout = config.getQueryTimeout();

        detectErrorOnMultipleSQL = config.isDetectErrorWhenMultiStatements();
    }

    public void open() throws SQLException {
        try {
            usePreparedStatement = config.isUsePreparedStatement();
            sql = config.getDataSet().getSqlQuery();

            if (usePreparedStatement) {
                log.debug("Prepared statement: " + sql);
                prepared_statement = conn.getConnection().prepareStatement(sql);
                if (useQueryTimeout) {
                    prepared_statement.setQueryTimeout(queryTimeout);
                }
            } else {
                statement = conn.getConnection().createStatement();
                if (useQueryTimeout) {
                    statement.setQueryTimeout(queryTimeout);
                }
            }
        } catch (SQLException e) {
            throw e;
        }
    }

    public void write(Record input) throws SQLException {
        totalCount++;

        cleanWrites();

        log.debug("Adding the record {} to the INSERT batch.", totalCount);
        try {
            if (usePreparedStatement) {
                log.debug("Prepared statement: " + config.getDataSet().getSqlQuery());
                JDBCRuntimeUtils.setPreparedStatement(prepared_statement, config.getPreparedStatementParameters());

                if (propagateQueryResultSet) {
                    resultSet = prepared_statement.executeQuery();
                } else {
                    prepared_statement.execute();
                    // In order to retrieve all the error messages, the method 'getMoreResults' needs to be called in
                    // loop.
                    // https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/Statement.html#getMoreResults()
                    if (detectErrorOnMultipleSQL) {
                        while (prepared_statement.getMoreResults() || prepared_statement.getLargeUpdateCount() != -1)
                            ;
                    }
                }
            } else {
                log.debug("Executing the query: '{}'", config.getDataSet().getSqlQuery());
                // Need to get updated sql query in case of dynamic(row) values usage
                if (propagateQueryResultSet) {
                    resultSet = statement.executeQuery(config.getDataSet().getSqlQuery());
                } else {
                    statement.execute(config.getDataSet().getSqlQuery());
                    if (detectErrorOnMultipleSQL) {
                        while (statement.getMoreResults() || statement.getLargeUpdateCount() != -1)
                            ;
                    }
                }
            }

            handleSuccess(input);
        } catch (SQLException e) {
            if (dieOnError) {
                throw e;
            } else {
                log.warn(e.getMessage());
                // TODO should not print it when reject line, but we can't know the information at the runtime
                System.err.println(e.getMessage());
            }

            handleReject(input, e);
        }

        try {
            executeCommit();
        } catch (SQLException e) {
            if (dieOnError) {
                throw e;
            } else {
                log.warn(e.getMessage());
            }
        }

    }

    public void close() throws SQLException {
        closeStatementQuietly(prepared_statement);
        prepared_statement = null;

        closeStatementQuietly(statement);
        statement = null;

        commitAndCloseAtLast();

        constructResult();
    }

    private void commitAndCloseAtLast() throws SQLException {
        if (useExistedConnection) {
            return;
        }

        try {
            if (useCommit && commitCount > 0) {
                commitCount = 0;

                if (conn != null) {
                    log.debug("Committing the transaction.");
                    conn.getConnection().commit();
                }
            }

            if (conn != null) {
                // need to call the commit before close for some database when do some read action like reading the
                // resultset
                if (useCommit) {
                    log.debug("Committing the transaction.");
                    conn.getConnection().commit();
                }
                log.debug("Closing connection");
                conn.close();
                conn = null;
            }
        } catch (SQLException e) {
            throw e;
        }
    }

    public List<Record> getSuccessfulWrites() {
        return Collections.unmodifiableList(successfulWrites);
    }

    public List<Record> getRejectedWrites() {
        return Collections.unmodifiableList(rejectedWrites);
    }

    private void cleanWrites() {
        successfulWrites.clear();
        rejectedWrites.clear();
    }

    private void handleSuccess(Record input) {
        successCount++;

        Record.Builder builder = recordBuilderFactory.newRecordBuilder(outSchema);

        if (input == null) {
            // if input is null, mean standalone mode or output mode
            if (propagateQueryResultSet) {
                Schema.Entry entry = outSchema.getEntry(config.getRecordSetColumn());
                if (entry == null) {
                    throw new RuntimeException(
                            "can't find the column : " + config.getRecordSetColumn() + " in " + rejectSchema);
                }
                builder.with(entry, resultSet);
            } else {
                // TODO no output or return empty record?
                return;
            }
        } else {
            for (Schema.Entry outField : outSchema.getEntries()) {
                Object outValue = null;

                if (propagateQueryResultSet && outField.getName().equals(config.getRecordSetColumn())) {
                    builder.with(outField, resultSet);
                } else {
                    Schema.Entry inField = input.getSchema().getEntry(outField.getName());
                    if (inField != null) {
                        outValue = input.get(Object.class, inField.getName());
                    }
                    builder.with(outField, outValue);
                }
            }
        }

        successfulWrites.add(builder.build());
    }

    private void handleReject(Record input, SQLException e) throws SQLException {
        rejectCount++;

        Record.Builder builder = recordBuilderFactory.newRecordBuilder(rejectSchema);

        if (input == null) {
            if (propagateQueryResultSet) {
                Schema.Entry entry = rejectSchema.getEntry(config.getRecordSetColumn());
                if (entry == null) {
                    throw new RuntimeException(
                            "can't find the column : " + config.getRecordSetColumn() + " in " + rejectSchema);
                }
                builder.with(entry, resultSet);

                builder.with(rejectSchema.getEntry("errorCode"), e.getSQLState());
                builder.with(rejectSchema.getEntry("errorMessage"), e.getMessage() + " - Line: " + totalCount);
            } else {
                // TODO no output or return empty record?
                return;
            }
        } else {
            for (Schema.Entry outField : rejectSchema.getEntries()) {
                Object outValue = null;
                Schema.Entry inField = input.getSchema().getEntry(outField.getName());

                if (inField != null) {
                    outValue = input.get(Object.class, inField.getName());
                } else if ("errorCode".equals(outField.getName())) {
                    outValue = e.getSQLState();
                } else if ("errorMessage".equals(outField.getName())) {
                    outValue = e.getMessage() + " - Line: " + totalCount;
                }

                builder.with(outField, outValue);
            }
        }

        Record reject = builder.build();

        rejectedWrites.add(reject);
    }

    private void executeCommit() throws SQLException {
        if (useCommit) {
            if (commitCount < commitEvery) {
                commitCount++;
            } else {
                commitCount = 0;
                log.debug("Committing the transaction.");
                conn.getConnection().commit();
            }
        }
    }

    private void closeStatementQuietly(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // close quietly
            }
        }
    }

    private void constructResult() {
    }

}
