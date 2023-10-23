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
package org.talend.components.jdbc.sp;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * the JDBC writer for JDBC SP
 *
 */
@Slf4j
public class JDBCSPWriter {

    private JDBCService.DataSourceWrapper conn;

    private JDBCSPConfig config;

    private boolean useExistedConnection;

    private CallableStatement cs;

    private final List<Record> successfulWrites = new ArrayList<>();

    private final List<Record> rejectedWrites = new ArrayList<>();

    private Schema componentSchema;

    private Schema outputSchema;

    protected final RecordBuilderFactory recordBuilderFactory;

    private int totalCount;

    public JDBCSPWriter(JDBCSPConfig config, boolean useExistedConnection, JDBCService.DataSourceWrapper conn,
            RecordBuilderFactory recordBuilderFactory) {
        this.config = config;
        this.useExistedConnection = useExistedConnection;
        this.conn = conn;
        this.recordBuilderFactory = recordBuilderFactory;

        componentSchema =
                SchemaInferer.convertSchemaInfoList2TckSchema(config.getSchema(), recordBuilderFactory);
        // TODO they are the same always?
        outputSchema = componentSchema;
    }

    public void open() throws SQLException {
        try {
            cs = conn.getConnection().prepareCall(getSPStatement());
        } catch (SQLException e) {
            throw e;
        }
    }

    private String getSPStatement() {
        String spName = config.getSpName();
        boolean isFunction = config.isFunction();
        List<SPParameter> parameterTypes = config.getSpParameters();

        StringBuilder statementBuilder = new StringBuilder();
        statementBuilder.append("{");

        if (isFunction) {
            statementBuilder.append("? = ");
        }

        statementBuilder.append("call ").append(spName).append("(");

        if (parameterTypes != null) {
            boolean first = true;
            for (SPParameter each : parameterTypes) {
                ParameterType parameterType = each.getParameterType();

                if (parameterType == ParameterType.RECORDSET) {
                    continue;
                }

                if (first) {
                    statementBuilder.append("?");
                    first = false;
                } else {
                    statementBuilder.append(",?");
                }
            }
        }

        statementBuilder.append(")}");
        log.debug("Statement: {}", statementBuilder);
        return statementBuilder.toString();
    }

    private JDBCSPRecordCreator recordCreator;

    public void write(Record inputRecord) throws SQLException {
        totalCount++;

        cleanWrites();

        try {
            if (inputRecord == null) {// standalone mode or output mode
                fillParameters(cs, componentSchema, null, null);
            } else {
                Schema inputSchema = inputRecord.getSchema();

                fillParameters(cs, componentSchema, inputSchema, inputRecord);
            }

            cs.execute();

            if (recordCreator == null) {
                recordCreator = new JDBCSPRecordCreator();
                recordCreator.init(componentSchema, outputSchema, config, recordBuilderFactory);
            }

            Record outputRecord = recordCreator.createRecord(cs, inputRecord);

            successfulWrites.add(outputRecord);
        } catch (Exception e) {
            throw e;
        }

    }

    private void fillParameters(CallableStatement cs, Schema componentSchema, Schema inputSchema, Record inputRecord)
            throws SQLException {
        if (config.isFunction()) {
            String columnName = config.getResultColumn();
            fillOutParameter(cs, componentSchema, columnName, 1);
        }

        List<SPParameter> spParameters = config.getSpParameters();
        if (spParameters != null) {
            int i = config.isFunction() ? 2 : 1;
            int j = -1;
            for (SPParameter each : spParameters) {
                j++;
                String columnName = each.getColumnName();

                ParameterType pt = each.getParameterType();

                if (ParameterType.RECORDSET == pt) {
                    continue;
                }

                if (ParameterType.OUT == pt || ParameterType.INOUT == pt) {
                    fillOutParameter(cs, componentSchema, columnName, i);
                }

                if (ParameterType.IN == pt || ParameterType.INOUT == pt) {
                    if (inputRecord != null) {
                        Schema.Entry inField = CommonUtils.getField(componentSchema, columnName);
                        Schema.Entry inFieldInInput = CommonUtils.getField(inputSchema, columnName);
                        JDBCMapping.setValue(i, cs, inField, inputRecord.get(Object.class, inFieldInInput.getName()));
                    } else {
                        throw new RuntimeException("input must exists for IN or INOUT parameters");
                    }
                }

                i++;
            }
        }
    }

    private void fillOutParameter(CallableStatement cs, Schema componentSchema, String columnName, int i)
            throws SQLException {
        Schema.Entry outField = CommonUtils.getField(componentSchema, columnName);
        cs.registerOutParameter(i, JDBCMapping.getSQLTypeFromTckType(outField));
    }

    public void close() throws SQLException {
        closeStatementQuietly(cs);

        closeAtLast();
    }

    private void closeAtLast() throws SQLException {
        if (useExistedConnection) {
            return;
        }

        try {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        } catch (SQLException e) {
            throw e;
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

}
