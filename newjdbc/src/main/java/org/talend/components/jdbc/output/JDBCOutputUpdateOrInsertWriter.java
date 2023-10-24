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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JDBCOutputUpdateOrInsertWriter extends JDBCOutputWriter {

    private String sqlInsert;

    private String sqlUpdate;

    private PreparedStatement statementInsert;

    private PreparedStatement statementUpdate;

    public JDBCOutputUpdateOrInsertWriter(JDBCOutputConfig config, final JDBCService jdbcService,
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
                    sqlInsert = JDBCSQLBuilder.getInstance()
                            .generateSQL4Insert(platform, config.getDataSet().getTableName(), columnList);
                    statementInsert = conn.getConnection().prepareStatement(sqlInsert);

                    sqlUpdate = JDBCSQLBuilder.getInstance()
                            .generateSQL4Update(platform, config.getDataSet().getTableName(), columnList);
                    statementUpdate = conn.getConnection().prepareStatement(sqlUpdate);
                } catch (SQLException e) {
                    throw e;
                }
            }

            initSchema = true;
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

        String updateSql_fact = rowWriter4Update.write(input);
        if (updateSql_fact != null) {
            context.set("QUERY", updateSql_fact);
            if (config.isDebugQuery()) {
                log.debug("'" + updateSql_fact.trim() + "'.");
            }
        }

        try {
            int count = statementUpdate.executeUpdate();
            updateCount += count;

            boolean noDataUpdate = (count == 0);

            if (noDataUpdate) {
                String insertSql_fact = rowWriter4Insert.write(input);
                if (insertSql_fact != null) {
                    context.set("QUERY", insertSql_fact);
                    if (config.isDebugQuery()) {
                        log.debug("'" + insertSql_fact.trim() + "'.");
                    }
                }
                insertCount += execute(input, statementInsert);
            } else {
                totalCount++;
                handleSuccess(input);
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
        closeStatementQuietly(statementUpdate);
        closeStatementQuietly(statementInsert);

        statementUpdate = null;
        statementInsert = null;

        commitAndCloseAtLast();

        constructResult();
    }

}
