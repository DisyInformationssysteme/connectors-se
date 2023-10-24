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
import org.talend.components.jdbc.output.RowWriter;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
public class Insert extends QueryManagerImpl {

    public Insert(final Platform platform, final JDBCOutputConfig configuration, final I18nMessage i18n,
            final RecordBuilderFactory recordBuilderFactory) {
        super(platform, configuration, i18n, recordBuilderFactory);
    }

    @Override
    public PreparedStatement buildQuery(final List<Record> records, final Connection connection) throws SQLException {
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
                .createColumnList(getConfiguration(), currentSchema, getConfiguration().isUseOriginColumnName(), null,
                        null);
        final String sql = JDBCSQLBuilder.getInstance()
                .generateSQL4Insert(getPlatform(), getConfiguration().getDataSet().getTableName(), columnList);

        final PreparedStatement statement = connection.prepareStatement(sql);

        final List<JDBCSQLBuilder.Column> columnList4Statement = new ArrayList<>();
        for (JDBCSQLBuilder.Column column : columnList) {
            if (column.addCol || (column.isReplaced())) {
                continue;
            }

            if (column.insertable) {
                columnList4Statement.add(column);
            }
        }

        rowWriter = new RowWriter(columnList4Statement, inputSchema, currentSchema, statement,
                getConfiguration().isDebugQuery(), sql);

        return statement;
    }

}