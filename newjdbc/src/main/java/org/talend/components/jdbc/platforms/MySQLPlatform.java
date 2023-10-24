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

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.schema.DbmsType;
import org.talend.components.jdbc.schema.TalendType;
import org.talend.components.jdbc.schema.TalendTypeAndTckTypeConverter;
import org.talend.components.jdbc.service.I18nMessage;
import org.talend.sdk.component.api.record.SchemaProperty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * syntax detail can be found at <a href=
 * "https://dev.mysql.com/doc/refman/8.0/en/create-table.html">https://dev.mysql.com/doc/refman/8.0/en/create-table.html</a>
 */
@Slf4j
public class MySQLPlatform extends Platform {

    public static final String MYSQL = "mysql";

    public MySQLPlatform(final I18nMessage i18n, final JDBCConfiguration.Driver driver) {
        super(i18n, driver);
    }

    @Override
    public String name() {
        return MYSQL;
    }

    @Override
    public String delimiterToken() {
        return "`";
    }

    @Override
    public void addDataSourceProperties(HikariDataSource dataSource) {
        super.addDataSourceProperties(dataSource);
        dataSource.addDataSourceProperty("useCursorFetch", true);
        String jdbcUrl = dataSource.getJdbcUrl();
        if (jdbcUrl != null && !jdbcUrl.contains("enabledTLSProtocols=")) {
            dataSource.addDataSourceProperty("enabledTLSProtocols", "TLSv1.2,TLSv1.1,TLSv1");
        }
    }

    @Override
    protected String buildQuery(final Connection connection, final Table table, final boolean useOriginColumnName,
            Dbms mapping)
            throws SQLException {
        // keep the string builder for readability
        final StringBuilder sql = new StringBuilder("CREATE TABLE");
        sql.append(" ");
        sql.append("IF NOT EXISTS");
        sql.append(" ");
        if (table.getSchema() != null && !table.getSchema().isEmpty()) {
            sql.append(identifier(table.getSchema())).append(".");
        }
        sql.append(identifier(table.getName()));
        sql.append("(");
        sql.append(createColumns(table.getColumns(), useOriginColumnName, mapping));
        sql
                .append(createPKs(connection.getMetaData(), table.getName(),
                        table.getColumns().stream().filter(Column::isPrimaryKey).collect(Collectors.toList())));
        sql.append(")");
        // todo create index

        log.debug("### create table query ###");
        log.debug(sql.toString());
        return sql.toString();
    }

    @Override
    protected boolean isTableExistsCreationError(final Throwable e) {
        return false;
    }

}
