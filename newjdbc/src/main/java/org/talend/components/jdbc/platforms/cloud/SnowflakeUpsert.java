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

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.output.JDBCSQLBuilder;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
public class SnowflakeUpsert extends UpsertDefault {

    SnowflakeCopyService snowflakeCopy = new SnowflakeCopyService();

    public SnowflakeUpsert(Platform platform, JDBCOutputConfig configuration, I18nMessage i18n,
            RecordBuilderFactory recordBuilderFactory) {
        super(platform, configuration, i18n, recordBuilderFactory);
        snowflakeCopy.setUseOriginColumnName(configuration.isUseOriginColumnName());
    }

    public void updateTargetTableFromTmpTable(final List<Record> records, final Connection connection,
            final String targetTable, final String tmpTable) throws SQLException {
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
                .createColumnList(getConfiguration(), currentSchema, getConfiguration().isUseOriginColumnName(),
                        getKeys(), getIgnoreColumns());

        final String sql = JDBCSQLBuilder.getInstance()
                .generateSQL4SnowflakeUpsert(getPlatform(), targetTable, tmpTable, columnList);

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public List<Reject> execute(final List<Record> records, final JDBCService.DataSourceWrapper dataSource)
            throws SQLException {
        final List<Reject> rejects = new ArrayList<>();
        final Connection connection = dataSource.getConnection();
        final String tableName = getConfiguration().getDataSet().getTableName();
        final String tmpTableName = snowflakeCopy.tmpTableName(tableName);
        final String fqTableName = namespace(connection) + "." + getPlatform().identifier(tableName);
        final String fqTmpTableName = namespace(connection) + "." + getPlatform().identifier(tmpTableName);
        final String fqStageName = namespace(connection) + ".%" + getPlatform().identifier(tmpTableName);
        rejects.addAll(snowflakeCopy.putAndCopy(connection, records, fqStageName, fqTableName, fqTmpTableName,
                getConfiguration(), getRecordBuilderFactory()));
        if (records.size() != rejects.size()) {
            try {
                updateTargetTableFromTmpTable(records, connection, fqTableName, fqTmpTableName);
                connection.commit();
            } finally {
                snowflakeCopy.cleanTmpFiles();
            }
        }
        return rejects;
    }
}
