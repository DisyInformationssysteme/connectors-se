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

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JDBCOutputInsertOrUpdateWriter extends JDBCOutputWriter {

    private String sqlQuery;

    private String sqlInsert;

    private String sqlUpdate;

    private PreparedStatement statementQuery;

    private PreparedStatement statementInsert;

    private PreparedStatement statementUpdate;

    public JDBCOutputInsertOrUpdateWriter(JDBCOutputConfig config, final JDBCService jdbcService,
            boolean useExistedConnection,
            JDBCService.DataSourceWrapper conn,
            RecordBuilderFactory recordBuilderFactory, RuntimeContextHolder context) {
        super(config, jdbcService, useExistedConnection, conn, recordBuilderFactory, context);
    }

    @Override
    public void open() throws SQLException {
        super.open();
        try {
            if (!isDynamic) {
                sqlQuery = JDBCSQLBuilder.getInstance()
                        .generateQuerySQL4InsertOrUpdate(platform, config.getDataSet().getTableName(), columnList);
                statementQuery = conn.getConnection().prepareStatement(sqlQuery);

                sqlInsert =
                        JDBCSQLBuilder.getInstance()
                                .generateSQL4Insert(platform, config.getDataSet().getTableName(), columnList);
                statementInsert = conn.getConnection().prepareStatement(sqlInsert);

                sqlUpdate =
                        JDBCSQLBuilder.getInstance()
                                .generateSQL4Update(platform, config.getDataSet().getTableName(), columnList);
                statementUpdate = conn.getConnection().prepareStatement(sqlUpdate);
            }
        } catch (SQLException e) {
            throw e;
        }

    }

    private RowWriter rowWriter4Query = null;

    private RowWriter rowWriter4Update = null;

    private RowWriter rowWriter4Insert = null;

    private boolean initSchema;

    private Schema currentSchema;

    private void initRowWriterIfNot(Schema inputSchema) throws SQLException {
        if (!initSchema) {
            currentSchema = componentSchema;
            if (isDynamic) {
                try {
                    currentSchema = SchemaInferer.mergeRuntimeSchemaAndDesignSchema4Dynamic(
                            config.getDataSet().getSchema(), inputSchema,
                            recordBuilderFactory);
                    columnList = JDBCSQLBuilder.getInstance().createColumnList(config, currentSchema);
                    sqlQuery = JDBCSQLBuilder.getInstance()
                            .generateQuerySQL4InsertOrUpdate(platform, config.getDataSet().getTableName(), columnList);
                    statementQuery = conn.getConnection().prepareStatement(sqlQuery);

                    sqlUpdate = JDBCSQLBuilder.getInstance()
                            .generateSQL4Update(platform, config.getDataSet().getTableName(), columnList);
                    statementUpdate = conn.getConnection().prepareStatement(sqlUpdate);

                    sqlInsert = JDBCSQLBuilder.getInstance()
                            .generateSQL4Insert(platform, config.getDataSet().getTableName(), columnList);
                    statementInsert = conn.getConnection().prepareStatement(sqlInsert);
                } catch (SQLException e) {
                    throw e;
                }
            }

            initSchema = true;
        }

        if (rowWriter4Query == null) {
            List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
            for (JDBCSQLBuilder.Column column : columnList) {
                if (column.addCol || (column.isReplaced())) {
                    continue;
                }

                if (column.updateKey) {
                    columnList4Statement.add(column);
                }
            }

            rowWriter4Query = new RowWriter(columnList4Statement, inputSchema, currentSchema, statementQuery);
        }

        if (rowWriter4Update == null) {
            List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
            for (JDBCSQLBuilder.Column column : columnList) {
                if (column.addCol || (column.isReplaced())) {
                    continue;
                }

                if (column.updatable) {
                    columnList4Statement.add(column);
                }
            }

            for (JDBCSQLBuilder.Column column : columnList) {
                if (column.addCol || (column.isReplaced())) {
                    continue;
                }

                if (column.updateKey) {
                    columnList4Statement.add(column);
                }
            }

            rowWriter4Update = new RowWriter(columnList4Statement, inputSchema, currentSchema, statementUpdate,
                    config.isDebugQuery(), sqlUpdate);
        }

        if (rowWriter4Insert == null) {
            List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
            for (JDBCSQLBuilder.Column column : columnList) {
                if (column.addCol || (column.isReplaced())) {
                    continue;
                }

                if (column.insertable) {
                    columnList4Statement.add(column);
                }
            }

            rowWriter4Insert = new RowWriter(columnList4Statement, inputSchema, currentSchema, statementInsert,
                    config.isDebugQuery(), sqlInsert);
        }
    }

    @Override
    public void write(Record input) throws SQLException {
        super.write(input);

        Schema inputSchema = input.getSchema();

        initRowWriterIfNot(inputSchema);

        boolean dataExists = false;

        try {
            rowWriter4Query.write(input);

            try (ResultSet resultSet = statementQuery.executeQuery()) {
                while (resultSet.next()) {
                    dataExists = resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            throw e;
        }

        try {
            if (dataExists) {// do update
                String sql_fact = rowWriter4Update.write(input);
                if (sql_fact != null) {
                    context.set("QUERY", sql_fact);
                    if (config.isDebugQuery()) {
                        log.debug("'" + sql_fact.trim() + "'.");
                    }
                }

                updateCount += execute(input, statementUpdate);
            } else {// do insert
                String sql_fact = rowWriter4Insert.write(input);
                if (sql_fact != null) {
                    context.set("QUERY", sql_fact);
                    if (config.isDebugQuery()) {
                        log.debug("'" + sql_fact.trim() + "'.");
                    }
                }

                insertCount += execute(input, statementInsert);
            }
        } catch (SQLException e) {
            if (dieOnError) {
                throw e;
            } else {
                totalCount++;

                System.err.println(e.getMessage());
                log.warn(e.getMessage());
            }

            handleReject(input, e);
        }

        try {
            executeCommit(null);
        } catch (SQLException e) {
            if (dieOnError) {
                throw e;
            } else {
                log.warn(e.getMessage());
            }
        }
    }

    @Override
    public void close() throws SQLException {
        closeStatementQuietly(statementQuery);
        closeStatementQuietly(statementUpdate);
        closeStatementQuietly(statementInsert);

        statementQuery = null;
        statementUpdate = null;
        statementInsert = null;

        commitAndCloseAtLast();

        constructResult();
    }

}
