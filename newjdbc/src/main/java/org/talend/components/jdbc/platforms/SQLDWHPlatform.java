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
package org.talend.components.jdbc.platforms;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.DistributionStrategy;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.common.RedshiftSortStrategy;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Slf4j
public class SQLDWHPlatform extends MSSQLPlatform {

    public static final String SQLDWH = "sqldwh";

    public SQLDWHPlatform(I18nMessage i18n, final JDBCConfiguration.Driver driver) {
        super(i18n, driver);
    }

    @Override
    public void createTableIfNotExist(final Connection connection,
            final List<Record> records, final Dbms mapping, final JDBCOutputConfig config,
            final RecordBuilderFactory recordBuilderFactory)
            throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        final Table tableModel =
                getTableModel(connection, records, config, recordBuilderFactory);
        final String sql = buildQuery(connection, tableModel, config.isUseOriginColumnName(), mapping);

        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            connection.commit();
        } catch (final Throwable e) {
            if (!isTableExistsCreationError(e)) {
                throw e;
            }

            log
                    .trace("create table issue was ignored. The table and it's name space has been created by an other worker",
                            e);
        }
    }

    @Override
    protected String buildQuery(final Connection connection, final Table table, final boolean useOriginColumnName,
            Dbms mapping)
            throws SQLException {
        // keep the string builder for readability
        final StringBuilder sql = new StringBuilder("CREATE TABLE");
        sql.append(" ");
        if (table.getSchema() != null && !table.getSchema().isEmpty()) {
            sql.append(identifier(table.getSchema())).append(".");
        }
        sql.append(identifier(table.getName()));
        sql.append("(");
        sql.append(createColumns(table.getColumns(), useOriginColumnName, mapping));
        sql.append(")");

        log.debug("### create table query ###");
        log.debug(sql.toString());
        return sql.toString();
    }

}
