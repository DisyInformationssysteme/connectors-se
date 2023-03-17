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
package org.talend.components.jdbc.service;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.talend.sdk.component.api.record.Schema.Type.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariDataSource;

import lombok.Data;
import org.talend.components.jdbc.configuration.JdbcConfiguration;
import org.talend.components.jdbc.configuration.JdbcConfiguration.Driver;
import org.talend.components.jdbc.dataset.BaseDataSet;
import org.talend.components.jdbc.dataset.TableNameDataset;
import org.talend.components.jdbc.datastore.AuthenticationType;
import org.talend.components.jdbc.datastore.JdbcConnection;
import org.talend.components.jdbc.output.platforms.Platform;
import org.talend.components.jdbc.output.platforms.PlatformService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.dependency.Resolver;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JdbcService {

    private static final String SNOWFLAKE_DATABASE_NAME = "Snowflake";

    private static final Pattern READ_ONLY_QUERY_PATTERN = Pattern
            .compile(
                    "^SELECT\\s+((?!((\\bINTO\\b)|(\\bFOR\\s+UPDATE\\b)|(\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b))).)+$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

    private final Map<JdbcConfiguration.Driver, URL[]> drivers = new HashMap<>();

    @Service
    private Resolver resolver;

    @Service
    private I18nMessage i18n;

    @Service
    private TokenClient tokenClient;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private PlatformService platformService;

    /**
     * @param query the query to check
     * @return return false if the sql query is not a read only query, true otherwise
     */
    public boolean isNotReadOnlySQLQuery(final String query) {
        return query != null && !READ_ONLY_QUERY_PATTERN.matcher(query.trim()).matches();
    }

    public static boolean checkTableExistence(final String tableName, final JdbcService.JdbcDatasource dataSource)
            throws SQLException {
        final String driverId = dataSource.getDriverId();
        try (final Connection connection = dataSource.getConnection()) {
            log.debug("getSchema(connection): " + getSchema(connection));
            try (final ResultSet resultSet = connection
                    .getMetaData()
                    .getTables(connection.getCatalog(), getSchema(connection),
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

    public JdbcDatasource createDataSource(final JdbcConnection connection) {
        return new JdbcDatasource(resolver, i18n, tokenClient, connection, this, false, false);
    }

    public JdbcDatasource createDataSource(final JdbcConnection connection, final boolean rewriteBatchedStatements) {
        return new JdbcDatasource(resolver, i18n, tokenClient, connection, this, false,
                rewriteBatchedStatements);
    }

    public JdbcDatasource createDataSource(final JdbcConnection connection, boolean isAutoCommit,
            final boolean rewriteBatchedStatements) {
        final JdbcConfiguration.Driver driver = platformService.getDriver(connection);
        return new JdbcDatasource(resolver, i18n, tokenClient, connection, this, isAutoCommit,
                rewriteBatchedStatements);
    }

    public PlatformService getPlatformService() {
        return this.platformService;
    }

    public static class JdbcDatasource implements AutoCloseable {

        private final Resolver.ClassLoaderDescriptor classLoaderDescriptor;

        private final HikariDataSource dataSource;

        @Getter
        private final String driverId;

        public JdbcDatasource(final Resolver resolver, final I18nMessage i18n, final TokenClient tokenClient,
                final JdbcConnection connection, final JdbcService jdbcService, final boolean isAutoCommit,
                final boolean rewriteBatchedStatements) {
            final Driver driver = jdbcService.getPlatformService().getDriver(connection);
            this.driverId = driver.getId();
            final Thread thread = Thread.currentThread();
            final ClassLoader prev = thread.getContextClassLoader();

            classLoaderDescriptor = resolver.mapDescriptorToClassLoader(driver.getPaths());
            if (!classLoaderDescriptor.resolvedDependencies().containsAll(driver.getPaths())) {
                String missingJars = driver
                        .getPaths()
                        .stream()
                        .filter(p -> !classLoaderDescriptor.resolvedDependencies().contains(p))
                        .collect(joining("\n"));
                throw new IllegalStateException(i18n.errorDriverLoad(driverId, missingJars));
            }

            try {
                thread.setContextClassLoader(classLoaderDescriptor.asClassLoader());
                dataSource = new HikariDataSource();
                if ("MSSQL_JTDS".equals(driverId)) {
                    dataSource.setConnectionTestQuery("SELECT 1");
                }
                if (!SNOWFLAKE_DATABASE_NAME.equals(connection.getDbType())
                        || AuthenticationType.BASIC == connection.getAuthenticationType()) {
                    dataSource.setUsername(connection.getUserId());
                    dataSource.setPassword(connection.getPassword());
                } else if (AuthenticationType.KEY_PAIR == connection.getAuthenticationType()) {
                    dataSource.setUsername(connection.getUserId());
                    dataSource
                            .addDataSourceProperty("privateKey",
                                    PrivateKeyUtils
                                            .getPrivateKey(connection.getPrivateKey(),
                                                    connection.getPrivateKeyPassword(), i18n));
                } else if (AuthenticationType.OAUTH == connection.getAuthenticationType()) {
                    dataSource.addDataSourceProperty("authenticator", "oauth");
                    dataSource
                            .addDataSourceProperty("token", OAuth2Utils.getAccessToken(connection, tokenClient, i18n));
                }
                dataSource.setDriverClassName(driver.getClassName());
                final Platform platform = jdbcService.getPlatformService().getPlatform(connection);
                final String jdbcUrl = platform.buildUrl(connection);
                dataSource.setJdbcUrl(jdbcUrl);
                if ("DeltaLake".equalsIgnoreCase(driverId)) {
                    // do nothing, DeltaLake default don't allow set auto commit to false
                } else {
                    dataSource.setAutoCommit(isAutoCommit);
                }
                dataSource.setMaximumPoolSize(1);
                dataSource.setConnectionTimeout(connection.getConnectionTimeOut() * 1000);
                dataSource.setValidationTimeout(connection.getConnectionValidationTimeOut() * 1000);
                jdbcService.getPlatformService().getPlatform(connection).addDataSourceProperties(dataSource);
                dataSource.addDataSourceProperty("rewriteBatchedStatements", String.valueOf(rewriteBatchedStatements));
                // dataSource.addDataSourceProperty("cachePrepStmts", "true");
                // dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
                // dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                // dataSource.addDataSourceProperty("useServerPrepStmts", "true");

                // Security Issues with LOAD DATA LOCAL https://jira.talendforge.org/browse/TDI-42001
                dataSource.addDataSourceProperty("allowLoadLocalInfile", "false"); // MySQL
                dataSource.addDataSourceProperty("allowLocalInfile", "false"); // MariaDB

                driver
                        .getFixedParameters()
                        .forEach(kv -> dataSource.addDataSourceProperty(kv.getKey(), kv.getValue()));

            } finally {
                thread.setContextClassLoader(prev);
            }
        }

        public Connection getConnection() throws SQLException {
            final Thread thread = Thread.currentThread();
            final ClassLoader prev = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoaderDescriptor.asClassLoader());
                return wrap(classLoaderDescriptor.asClassLoader(), dataSource.getConnection(), Connection.class);
            } finally {
                thread.setContextClassLoader(prev);
            }
        }

        @Override
        public void close() {
            final Thread thread = Thread.currentThread();
            final ClassLoader prev = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoaderDescriptor.asClassLoader());
                dataSource.close();
            } finally {
                thread.setContextClassLoader(prev);
                try {
                    classLoaderDescriptor.close();
                } catch (final Exception e) {
                    log.error("can't close driver classloader properly", e);
                }
            }
        }

        private static <T> T wrap(final ClassLoader classLoader, final Object delegate, final Class<T> api) {
            return api
                    .cast(
                            Proxy
                                    .newProxyInstance(classLoader, new Class<?>[] { api },
                                            new ContextualDelegate(delegate, classLoader)));
        }

        @AllArgsConstructor
        private static class ContextualDelegate implements InvocationHandler {

            private final Object delegate;

            private final ClassLoader classLoader;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                final Thread thread = Thread.currentThread();
                final ClassLoader prev = thread.getContextClassLoader();
                thread.setContextClassLoader(classLoader);
                try {
                    final Object invoked = method.invoke(delegate, args);
                    if (method.getReturnType().getName().startsWith("java.sql.")
                            && method.getReturnType().isInterface()) {
                        return wrap(classLoader, invoked, method.getReturnType());
                    }
                    return invoked;
                } catch (final InvocationTargetException ite) {
                    throw ite.getTargetException();
                } finally {
                    thread.setContextClassLoader(prev);
                }
            }
        }
    }

    public static String getSchema(Connection connection) throws SQLException {
        // Special code for MSSQL JDTS driver
        String schema = null;
        try {
            String result = connection.getSchema();
            // delta lake database driver return empty string which not follow jdbc spec.
            if (result != null && !"".equals(result)) {
                schema = result;
            }
        } catch (AbstractMethodError e) {
            // ignore
        }
        return schema;
    }

    @Data
    @AllArgsConstructor
    private static class ColumnMetadata {

        int size;

        int scale;

        int sqlType;

        boolean nullable;

        String columnLabel;// consider "select as" case

        String columnName;

        Object defaultValue;

        boolean isKey;

        boolean isUnique;

        boolean isForeignKey;

        String columnTypeName;

        String javaType;
    }

    public Schema createSchema(BaseDataSet dataSet, Connection connection, ResultSet resultSet,
            RecordBuilderFactory recordBuilderFactory) throws SQLException {
        final Map<String, ColumnMetadata> columnMetadata = new HashMap<>();
        if (TableNameDataset.class.isInstance(dataSet)) {
            String catalog = null;
            String dbSchema = null;
            // when we generate sql for table fetch, we wrap table name auto like : [select * from `Company`], table
            // name setting is [Company] in ui.
            // and user may set table name in ui like this : [catalog.schema1.Company], then generate the expected sql
            // like this : [select * from `catalog.schema1.Company`], that's wrong.
            // but if current database no wrap character, then generate the sql like this : [select * from
            // catalog.schema1.Company], then that works for query,
            // but will fail for the code below for table metadata fetch as table name is expected : [Company] without
            // the full name prefix [catalog.schema1.].
            String tableName = TableNameDataset.class.cast(dataSet).getTableName();
            try {
                catalog = connection.getCatalog();
                dbSchema = JdbcService.getSchema(connection);

                final DatabaseMetaData databaseMetadata = connection.getMetaData();

                final Set<String> keys = getPrimaryKeys(databaseMetadata, catalog, dbSchema, tableName);
                final Set<String> uniqueColumns = getUniqueColumns(databaseMetadata, catalog, dbSchema, tableName);
                final Set<String> foreignKeys = getForeignKeys(databaseMetadata, catalog, dbSchema, tableName);

                try (ResultSet tableMetadata = databaseMetadata.getColumns(catalog, dbSchema, tableName, null)) {
                    while (tableMetadata.next()) {
                        String columnName = tableMetadata.getString("COLUMN_NAME");

                        int size = tableMetadata.getInt("COLUMN_SIZE");
                        int scale = tableMetadata.getInt("DECIMAL_DIGITS");
                        int sqlType = tableMetadata.getInt("DATA_TYPE");
                        boolean nullable = DatabaseMetaData.columnNullable == tableMetadata.getInt("NULLABLE");

                        boolean isKey = keys.contains(columnName);

                        // primary key also create unique index, so exclude it here
                        boolean isUniqueColumn = isKey ? false : uniqueColumns.contains(columnName);

                        boolean isForeignKey = foreignKeys.contains(columnName);

                        String columnTypeName = tableMetadata.getString("TYPE_NAME");

                        columnMetadata.put(columnName,
                                new ColumnMetadata(size, scale, sqlType, nullable, columnName, columnName, null, isKey,
                                        isUniqueColumn, isForeignKey, columnTypeName, null));
                    }
                }
            } catch (Exception e) {
                log.error("[catalog,db schema,table] {} {} {}.", catalog, dbSchema, tableName);
                log.error("can't fetch table metadata : ", e);
            }
        }

        final Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(RECORD);
        final ResultSetMetaData resultSetMetadata = resultSet.getMetaData();

        for (int i = 1; i <= resultSetMetadata.getColumnCount(); i++) {
            String columnName = resultSetMetadata.getColumnName(i);

            String javaType = resultSetMetadata.getColumnClassName(i);

            // need to correct the metadata when use table fetch with/without column list instead of query
            ColumnMetadata column = columnMetadata.get(columnName);
            if (column != null) {
                column.setJavaType(javaType);
                addField(schemaBuilder, column);
            } else {
                String columnLabel = resultSetMetadata.getColumnLabel(i);

                int size = resultSetMetadata.getPrecision(i);
                int scale = resultSetMetadata.getScale(i);
                boolean nullable = ResultSetMetaData.columnNullable == resultSetMetadata.isNullable(i);

                int sqlType = resultSetMetadata.getColumnType(i);

                // not necessary for the result schema from the query statement
                boolean isKey = false;
                boolean isUniqueColumn = false;
                boolean isForeignKey = false;

                String columnTypeName = resultSetMetadata.getColumnTypeName(i).toUpperCase();

                addField(schemaBuilder,
                        new ColumnMetadata(size, scale, sqlType, nullable, columnLabel, columnName, null, isKey,
                                isUniqueColumn, isForeignKey, columnTypeName, javaType));
            }
        }

        return schemaBuilder.build();
    }

    private static Set<String> getPrimaryKeys(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getPrimaryKeys(catalogName, schemaName, tableName)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("COLUMN_NAME"));
                }
            }
        }

        return result;
    }

    private static Set<String> getUniqueColumns(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getIndexInfo(catalogName, schemaName, tableName, true, true)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    String indexColumn = resultSet.getString("COLUMN_NAME");
                    // some database return some null, for example oracle, so need this null check
                    if (indexColumn != null) {
                        result.add(indexColumn);
                    }
                }
            }
        }

        return result;
    }

    private static Set<String> getForeignKeys(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getImportedKeys(catalogName, schemaName, tableName)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("FKCOLUMN_NAME"));
                }
            }
        }

        return result;
    }

    private void addField(final Schema.Builder builder, ColumnMetadata column) {
        final Schema.Entry.Builder entryBuilder = recordBuilderFactory.newEntryBuilder()
                .withName(column.columnName)// not use columnLabel? now only keep like before
                .withNullable(column.isNullable())
                .withProp(SchemaProperty.ORIGIN_TYPE, String.valueOf(column.columnTypeName));

        if (column.isKey) {
            entryBuilder.withProp(SchemaProperty.IS_KEY, "true");
        }

        if (column.isUnique) {
            entryBuilder.withProp(SchemaProperty.IS_UNIQUE, "true");
        }

        if (column.isForeignKey) {
            entryBuilder.withProp(SchemaProperty.IS_FOREIGN_KEY, "true");
        }

        setSize(entryBuilder, column.size);

        switch (column.sqlType) {
        case java.sql.Types.SMALLINT:
        case java.sql.Types.TINYINT:
        case java.sql.Types.INTEGER:
            if (column.javaType.equals(Integer.class.getName()) || Short.class.getName().equals(column.javaType)) {
                builder.withEntry(entryBuilder.withType(INT).build());
            } else {
                builder.withEntry(entryBuilder.withType(LONG).build());
            }
            break;
        case java.sql.Types.FLOAT:
        case java.sql.Types.REAL:
            setScale(entryBuilder, column.scale);
            builder.withEntry(entryBuilder.withType(FLOAT).build());
            break;
        case java.sql.Types.DOUBLE:
            setScale(entryBuilder, column.scale);
            builder.withEntry(entryBuilder.withType(DOUBLE).build());
            break;
        case java.sql.Types.BOOLEAN:
            builder.withEntry(entryBuilder.withType(BOOLEAN).build());
            break;
        case java.sql.Types.TIME:
        case java.sql.Types.DATE:
        case java.sql.Types.TIMESTAMP:
            setScale(entryBuilder, column.scale);
            builder.withEntry(entryBuilder.withType(DATETIME).build());
            break;
        case java.sql.Types.BINARY:
        case java.sql.Types.VARBINARY:
        case java.sql.Types.LONGVARBINARY:
            builder.withEntry(entryBuilder.withType(BYTES).build());
            break;
        case java.sql.Types.DECIMAL:
        case java.sql.Types.NUMERIC:
            setScale(entryBuilder, column.scale);
            // TODO use new tck DECIMAL, but before do it, we need to adapter all processor connector in cloud
            builder.withEntry(entryBuilder.withType(STRING).build());
            break;
        case java.sql.Types.BIGINT:// why map to String here?
        case java.sql.Types.VARCHAR:
        case java.sql.Types.LONGVARCHAR:
        case java.sql.Types.CHAR:
            builder.withEntry(entryBuilder.withType(STRING).build());
            break;
        default:
            setScale(entryBuilder, column.scale);
            builder.withEntry(entryBuilder.withType(STRING).build());
            break;
        }

        log.warn("[addField] {} {} {}.", column.columnName, column.sqlType, column.javaType);
    }

    private static void setSize(Schema.Entry.Builder builder, int size) {
        if (size == 0)
            return;// it's impossible that size is 0 if the size have meaning for that database column type
        builder.withProp(SchemaProperty.SIZE, String.valueOf(size));
    }

    private static void setScale(Schema.Entry.Builder builder, int scale) {
        // that is possible that scale is 0, for example : DECIMAL(12, 0), and no way to decide if 0 scale is valid for
        // current database column type
        builder.withProp(SchemaProperty.SCALE, String.valueOf(scale));
    }

    public void addColumn(final Record.Builder builder, final Schema.Entry entry, final Object value) {
        switch (entry.getType()) {
        case INT:
            if (value != null) {
                if (value instanceof Integer) {
                    builder.withInt(entry, (Integer) value);
                } else if (value instanceof Short) {
                    builder.withInt(entry, ((Short) value).intValue());
                } else if (value instanceof Number) {
                    builder.withInt(entry, ((Number) value).intValue());
                }
            }
            break;
        case LONG:
            if (value != null) {
                builder.withLong(entry, Long.parseLong(value.toString()));
            }
            break;
        case FLOAT:
            if (value != null) {
                builder.withFloat(entry, (Float) value);
            }
            break;
        case DOUBLE:
            if (value != null) {
                builder.withDouble(entry, (Double) value);
            }
            break;
        case BOOLEAN:
            if (value != null) {
                builder.withBoolean(entry, (Boolean) value);
            }
            break;
        case DATETIME:
            builder
                    .withDateTime(entry,
                            value == null ? null : new Date(((java.util.Date) value).getTime()));
            break;
        case BYTES:
            builder.withBytes(entry, value == null ? null : (byte[]) value);
            break;
        case STRING:
        default:
            builder.withString(entry, value == null ? null : String.valueOf(value));
            break;
        }
    }
}
