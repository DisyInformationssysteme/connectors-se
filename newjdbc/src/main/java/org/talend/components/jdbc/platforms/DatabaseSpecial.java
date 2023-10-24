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
import org.talend.components.jdbc.common.AuthenticationType;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.service.JDBCService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

@Slf4j
public class DatabaseSpecial {

    public static void doConfig4DifferentDatabaseAndDifferentRuntimeEnv(final HikariDataSource dataSource,
            final JDBCDataStore dataStore, final List<String> driverPaths, final JDBCService jdbcService,
            final Map<String, String> additionalJDBCProperties) {
        final boolean isCloud = RuntimeEnvUtil.isCloud(dataStore);
        if (!isCloud) {
            dataSource.setJdbcUrl(dataStore.getJdbcUrl());
            dataSource.setDriverClassName(dataStore.getJdbcClass());

            // HikariCP force to use java.sql.Connetion.isValid or test query to check connection if valid,
            // so have to set here for different database if that driver not support isValid method
            if (dataStore.getJdbcClass() != null
                    && "net.sourceforge.jtds.jdbc.Driver".equals(dataStore.getJdbcClass())) {
                // the lastest jtds driver not support isValid method :
                // https://mvnrepository.com/artifact/net.sourceforge.jtds/jtds
                dataSource.setConnectionTestQuery("SELECT 1");
                // TODO check for sybase database as jtds also can works for that
            } else if (driverPaths.stream().anyMatch(path -> path.contains("ojdbc5"))) {
                // oracle ojdbc5 also not support isValid method
                dataSource.setConnectionTestQuery("SELECT 1 FROM DUAL");
            }

            if (dataStore.getUserId() != null) {
                dataSource.setUsername(dataStore.getUserId());
            }
            if (dataStore.getPassword() != null) {
                dataSource.setPassword(dataStore.getPassword());
            }
        } else {
            final JDBCConfiguration.Driver driver = jdbcService.getPlatformService().getDriver(dataStore);

            dataSource.setDriverClassName(driver.getClassName());
            final Platform platform = jdbcService.getPlatformService().getPlatform(dataStore);
            final String jdbcUrl = platform.buildUrl(dataStore);
            dataSource.setJdbcUrl(jdbcUrl);

            String driverId = driver.getId();
            if ("MSSQL_JTDS".equals(driverId)) {
                dataSource.setConnectionTestQuery("SELECT 1");
            }
            if (!"Snowflake".equals(dataStore.getDbType())
                    || AuthenticationType.BASIC == dataStore.getAuthenticationType()) {
                if (dataStore.getUserId() != null) {
                    dataSource.setUsername(dataStore.getUserId());
                }
                if (dataStore.getPassword() != null) {
                    dataSource.setPassword(dataStore.getPassword());
                }
            } else if (AuthenticationType.KEY_PAIR == dataStore.getAuthenticationType()) {
                dataSource.setUsername(dataStore.getUserId());
                dataSource
                        .addDataSourceProperty("privateKey",
                                PrivateKeyUtils
                                        .getPrivateKey(dataStore.getPrivateKey(),
                                                dataStore.getPrivateKeyPassword(), jdbcService.getI18n()));
            } else if (AuthenticationType.OAUTH == dataStore.getAuthenticationType()) {
                dataSource.addDataSourceProperty("authenticator", "oauth");
                dataSource
                        .addDataSourceProperty("token", OAuth2Utils.getAccessToken(dataStore,
                                jdbcService.getTokenClient(), jdbcService.getI18n()));
            }

            // TODO not set auto commit when deltalake database

            dataSource.setConnectionTimeout(dataStore.getConnectionTimeOut() * 1000);
            dataSource.setValidationTimeout(dataStore.getConnectionValidationTimeOut() * 1000);

            // Security Issues with LOAD DATA LOCAL https://jira.talendforge.org/browse/TDI-42001
            dataSource.addDataSourceProperty("allowLoadLocalInfile", "false"); // MySQL
            dataSource.addDataSourceProperty("allowLocalInfile", "false"); // MariaDB

            platform.addDataSourceProperties(dataSource);

            additionalJDBCProperties.entrySet()
                    .stream()
                    .forEach(kv -> dataSource.addDataSourceProperty(kv.getKey(), kv.getValue()));
            driver.getFixedParameters().forEach(kv -> dataSource.addDataSourceProperty(kv.getKey(), kv.getValue()));
        }

        // mysql special property?
        // this will make statement.executeBatch return wrong info for data insert/updte count, so disable it
        // for studio
        // dataSource.addDataSourceProperty("rewriteBatchedStatements", "true");
    }

    public static boolean checkTableExistence(final String driverId, final String tableName,
            final JDBCService.DataSourceWrapper dataSource) throws SQLException {
        final Connection connection = dataSource.getConnection();
        log.debug("getSchema(connection): " + JDBCService.getDatabaseSchema(connection));
        try (final ResultSet resultSet = connection
                .getMetaData()
                .getTables(connection.getCatalog(), JDBCService.getDatabaseSchema(connection),
                        "Redshift".equalsIgnoreCase(driverId) ? null : tableName,
                        new String[] { "TABLE", "SYNONYM" })) {
            while (resultSet.next()) {
                final String table_name = resultSet.getString("TABLE_NAME");
                log.debug("table_name " + table_name);
                if (ofNullable(ofNullable(table_name).orElseGet(() -> {
                    try {
                        return resultSet.getString("SYNONYM_NAME");
                    } catch (final SQLException e) {
                        return null;
                    }
                }))
                        .filter(tn -> ("DeltaLake".equalsIgnoreCase(driverId)
                                || "Redshift".equalsIgnoreCase(driverId)
                                        ? tableName.equalsIgnoreCase(tn)
                                        : tableName.equals(tn)))
                        .isPresent()) {
                    return true;
                }
            }
            return false;
        }
    }

}
