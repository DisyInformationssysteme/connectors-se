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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JDBCOutputInsertWriter extends JDBCOutputWriter {

    private String sql;

    public JDBCOutputInsertWriter(final JDBCOutputConfig config, final JDBCService jdbcService,
            final boolean useExistedConnection,
            final JDBCService.DataSourceWrapper conn,
            final RecordBuilderFactory recordBuilderFactory, final RuntimeContextHolder context) {
        super(config, jdbcService, useExistedConnection, conn, recordBuilderFactory, context);
    }

    @Override
    public void open() throws SQLException {
        super.open();
        try {
            // if not dynamic, we can computer it now for "fail soon" way, not fail in main part if fail
            if (!isDynamic) {
                sql = JDBCSQLBuilder.getInstance()
                        .generateSQL4Insert(platform, config.getDataSet().getTableName(), columnList);
                statement = conn.getConnection().prepareStatement(sql);
            }
        } catch (SQLException e) {
            throw e;
        }
    }

    private RowWriter rowWriter = null;

    private void initRowWriterIfNot(Schema inputSchema) throws SQLException {
        if (rowWriter == null) {
            Schema currentSchema = componentSchema;
            if (isDynamic) {
                try {
                    currentSchema = SchemaInferer.mergeRuntimeSchemaAndDesignSchema4Dynamic(
                            config.getDataSet().getSchema(), inputSchema,
                            recordBuilderFactory);
                    columnList = JDBCSQLBuilder.getInstance().createColumnList(config, currentSchema);
                    sql = JDBCSQLBuilder.getInstance()
                            .generateSQL4Insert(platform, config.getDataSet().getTableName(), columnList);
                    statement = conn.getConnection().prepareStatement(sql);
                } catch (SQLException e) {
                    throw e;
                }
            }

            List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
            for (JDBCSQLBuilder.Column column : columnList) {
                if (column.addCol || (column.isReplaced())) {
                    continue;
                }

                if (column.insertable) {
                    columnList4Statement.add(column);
                }
            }

            rowWriter = new RowWriter(columnList4Statement, inputSchema, currentSchema, statement,
                    config.isDebugQuery(), sql);
        }
    }

    @Override
    public void write(Record input) throws SQLException {
        super.write(input);

        Schema inputSchema = input.getSchema();

        initRowWriterIfNot(inputSchema);

        String sql_fact = rowWriter.write(input);
        if (sql_fact != null) {
            context.set("QUERY", sql_fact);
            if (config.isDebugQuery()) {
                log.debug("'" + sql_fact.trim() + "'.");
            }
        }

        try {
            insertCount += execute(input, statement);
        } catch (SQLException e) {
            if (dieOnError) {
                throw e;
            } else {
                // TODO when use reject, should not print it, but now no method to know if we use the reject line in the
                // job
                // design at run time.
                // System.err.print(e.getMessage());

                // also it seems that we should not use the System.err in future, should use log instead of it.
                System.err.println(e.getMessage());
                log.warn(e.getMessage());
            }

            handleReject(input, e);
        }

        try {
            insertCount += executeCommit(statement);
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
        // execute the batch to make everything is passed to the server side before release the resource
        insertCount += executeBatchAtLast();

        closeStatementQuietly(statement);
        statement = null;

        commitAndCloseAtLast();

        constructResult();
    }

}
