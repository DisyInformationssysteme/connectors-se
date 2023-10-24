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
import org.talend.components.jdbc.platforms.GenericPlatform;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.platforms.RuntimeEnvUtil;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * common JDBC writer
 *
 */
@Slf4j
abstract public class JDBCOutputWriter {

    protected JDBCService.DataSourceWrapper conn;

    protected int successCount;

    protected int rejectCount;

    protected boolean useBatch;

    protected int batchSize;

    protected int batchCount;

    protected int commitEvery;

    protected int commitCount;

    protected boolean useCommit;

    protected boolean useExistedConnection;

    protected boolean dieOnError;

    protected PreparedStatement statement;

    protected int insertCount;

    protected int updateCount;

    protected int deleteCount;

    protected List<JDBCSQLBuilder.Column> columnList;

    protected Schema componentSchema;

    protected Schema rejectSchema;

    protected boolean isDynamic;

    protected Boolean useQueryTimeout;

    protected Integer queryTimeout;

    protected JDBCOutputConfig config;

    protected final List<Record> successfulWrites = new ArrayList<>();

    protected final List<Record> rejectedWrites = new ArrayList<>();

    protected final RecordBuilderFactory recordBuilderFactory;

    protected final RuntimeContextHolder context;

    protected int totalCount;

    protected final boolean isCloud;

    protected final Platform platform;

    public JDBCOutputWriter(final JDBCOutputConfig config, final JDBCService jdbcService,
            final boolean useExistedConnection, final JDBCService.DataSourceWrapper conn,
            final RecordBuilderFactory recordBuilderFactory, final RuntimeContextHolder context) {
        this.config = config;

        this.isCloud = RuntimeEnvUtil.isCloud(config.getDataSet().getDataStore());
        if (isCloud) {
            this.platform = jdbcService.getPlatformService().getPlatform(config.getDataSet().getDataStore());
        } else {
            this.platform = new GenericPlatform(jdbcService.getI18n(), null);
        }

        this.useExistedConnection = useExistedConnection;
        this.conn = conn;
        this.recordBuilderFactory = recordBuilderFactory;
        this.context = context;

        if (context != null) {
            bufferSizeKey4Parallelize = "buffersSizeKey_" + context.getConnectorId() + "_"
                    + Thread.currentThread().getId();
        }

        useBatch = config.isUseBatch();
        DataAction dataAction = config.getDataAction();
        if ((dataAction == DataAction.INSERT_OR_UPDATE) || (dataAction == DataAction.UPDATE_OR_INSERT)) {
            useBatch = false;
        }
        if (useBatch) {
            batchSize = config.getBatchSize();
        }

        if (!useExistedConnection) {
            commitEvery = config.getCommitEvery();
            if (commitEvery > 0) {
                useCommit = true;
            }
        }

        dieOnError = config.isDieOnError();

        useQueryTimeout = config.isUseQueryTimeout();
        if (useQueryTimeout) {
            queryTimeout = config.getQueryTimeout();
        }
    }

    public void open() throws SQLException {
        log.debug("JDBCOutputWriter start.");
        componentSchema =
                SchemaInferer.convertSchemaInfoList2TckSchema(config.getDataSet().getSchema(), recordBuilderFactory);
        // no way to fetch reject schema, but workaround : add columns here as reject schema add "errorCode" and
        // "errorMessage" columns base on componentSchema
        rejectSchema = SchemaInferer.getRejectSchema(config.getDataSet().getSchema(), recordBuilderFactory);

        isDynamic =
                componentSchema.getEntries().isEmpty() || SchemaInferer.containDynamic(config.getDataSet().getSchema());

        // if not dynamic, we can computer it now for "fail soon" way, not fail in main part if fail
        if (!isDynamic) {
            columnList = JDBCSQLBuilder.getInstance().createColumnList(config, componentSchema);
        }

        if (!config.isClearData()) {
            return;
        }

        String sql = JDBCSQLBuilder.getInstance().generateSQL4DeleteTable(platform, config.getDataSet().getTableName());
        try {
            try (Statement statement = conn.getConnection().createStatement()) {
                if (useQueryTimeout) {
                    statement.setQueryTimeout(queryTimeout);
                }
                log.debug("Executing the query: '{}'", sql);
                deleteCount += statement.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw e;
        }

    }

    private String bufferSizeKey4Parallelize;

    public void write(Record record) throws SQLException {
        if (context != null) {
            Object bufferSizeObject = context.getGlobal(bufferSizeKey4Parallelize);
            if (bufferSizeObject != null) {
                int bufferSize = (int) bufferSizeObject;
                commitEvery = bufferSize;
                batchSize = bufferSize;
            }
        }

        // the var(result.totalCount) is equals with the old "nb_line" var, but the old one is a little strange in
        // tjdbcoutput, i
        // don't know why,
        // maybe a bug, but
        // now only keep the old action for the tujs :
        // 1: insert action, update action : plus after addbatch or executeupdate
        // 2: insert or update action, update or insert action, delete action : plus after addbatch(delete action) or
        // executeupdate, also when not die on error and exception appear

        // result.totalCount++;

        cleanWrites();
    }

    abstract public void close() throws SQLException;

    protected void commitAndCloseAtLast() throws SQLException {
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

    protected void handleSuccess(Record input) {
        successCount++;
        successfulWrites.add(input);
    }

    protected void handleReject(Record input, SQLException e) {
        if (useBatch) {
            return;
        }

        rejectCount++;

        Record.Builder builder = recordBuilderFactory.newRecordBuilder(rejectSchema);
        for (Schema.Entry rejectField : rejectSchema.getEntries()) {
            Object rejectValue = null;
            // getField is a O(1) method for time, so performance is OK here.
            Schema.Entry inField = input.getSchema().getEntry(rejectField.getName());

            if (inField != null) {
                rejectValue = input.get(Object.class, inField.getName());
            } else if ("errorCode".equals(rejectField.getName())) {
                rejectValue = e.getSQLState();
            } else if ("errorMessage".equals(rejectField.getName())) {
                rejectValue = e.getMessage() + " - Line: " + totalCount;
            }

            builder.with(rejectField, rejectValue);
        }

        Record reject = builder.build();
        rejectedWrites.add(reject);
    }

    protected int executeCommit(PreparedStatement statement) throws SQLException {
        int result = 0;

        if (!useCommit) {
            return result;
        }

        commitCount++;

        if (commitCount < commitEvery) {

        } else {
            commitCount = 0;

            // execute the batch to make everything is passed to the server side before commit something
            if (useBatch && batchCount > 0) {
                result += executeBatchAndGetCount(statement);
                batchCount = 0;
            }
            log.debug("Committing the transaction.");
            conn.getConnection().commit();
        }

        return result;
    }

    protected int execute(Record input, PreparedStatement statement) throws SQLException {
        int count = 0;

        if (useBatch) {
            statement.addBatch();

            totalCount++;

            batchCount++;

            if (batchCount < batchSize) {

            } else {
                batchCount = 0;
                count = executeBatchAndGetCount(statement);
            }
        } else {
            if (useQueryTimeout) {
                statement.setQueryTimeout(queryTimeout);
            }
            log.debug("Executing statement");
            count = statement.executeUpdate();

            totalCount++;
        }

        handleSuccess(input);

        return count;
    }

    protected int executeBatchAndGetCount(PreparedStatement statement) throws SQLException {
        int result = 0;

        try {
            if (useQueryTimeout) {
                statement.setQueryTimeout(queryTimeout);
            }
            log.debug("Executing batch");
            int[] batchResult = statement.executeBatch();
            result += sum(batchResult);
        } catch (BatchUpdateException e) {
            if (dieOnError) {
                throw e;
            } else {
                int[] batchResult = e.getUpdateCounts();
                result += sum(batchResult);

                log.warn(e.getMessage());
            }
        }
        log.debug("Executing statement");
        int count = statement.getUpdateCount();

        result = Math.max(result, count);

        return result;
    }

    private int sum(int[] batchResult) {
        int result = 0;
        for (int count : batchResult) {
            result += Math.max(count, 0);
        }
        return result;
    }

    protected void closeStatementQuietly(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // close quietly
            }
        }
    }

    protected void constructResult() {
        if (context != null) {
            context.set("NB_LINE_DELETED", deleteCount);
            context.set("NB_LINE_INSERTED", insertCount);
            context.set("NB_LINE_UPDATED", updateCount);
            context.set("NB_LINE_REJECTED", rejectCount);

            context.set("NB_LINE", totalCount);
        }
    }

    protected int executeBatchAtLast() throws SQLException {
        if (useBatch && batchCount > 0) {
            try {
                batchCount = 0;
                return executeBatchAndGetCount(statement);
            } catch (SQLException e) {
                if (dieOnError) {
                    throw e;
                } else {
                    log.warn(e.getMessage());
                }
            }
        }

        return 0;
    }

}
