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

import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.DBType;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.common.RedshiftSortStrategy;
import org.talend.components.jdbc.dataset.JDBCQueryDataSet;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.input.JDBCInputConfig;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.platforms.*;
import org.talend.components.jdbc.row.JDBCRowConfig;
import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.schema.JDBCTableMetadata;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.context.RuntimeContext;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.asyncvalidation.AsyncValidation;
import org.talend.sdk.component.api.service.asyncvalidation.ValidationResult;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.completion.Suggestions;
import org.talend.sdk.component.api.service.connection.CloseConnection;
import org.talend.sdk.component.api.service.connection.CloseConnectionObject;
import org.talend.sdk.component.api.service.connection.CreateConnection;
import org.talend.sdk.component.api.service.dependency.Resolver;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.api.service.schema.DiscoverSchemaExtended;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

// if want to inject context successful, need implement Serializable, seems the limit come from tck framework, TODO
// check why
@Slf4j
@Service
public class JDBCService implements Serializable {

    private static final long serialVersionUID = 1;

    @Service
    private Resolver resolver;

    // this should be used in @CreateConnection and @CloseConnection action method,
    // that method should not be code called outside of JDBCService
    @RuntimeContext
    private transient RuntimeContextHolder context;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Getter
    @Service
    private TokenClient tokenClient;

    @Getter
    @Service
    private PlatformService platformService;

    @Getter
    @Service
    private I18nMessage i18n;

    @Suggestions("GUESS_DRIVER_CLASS")
    public SuggestionValues loadRecordTypes(@Option final List<org.talend.components.jdbc.common.Driver> driverJars)
            throws Exception {
        final List<SuggestionValues.Item> items = new ArrayList<>();

        // items.add(new SuggestionValues.Item("com.mysql.cj.jdbc.Driver", "com.mysql.cj.jdbc.Driver"));

        getDriverClasses(driverJars).stream().forEach(driverClass -> {
            items.add(new SuggestionValues.Item(driverClass, driverClass));
        });

        return new SuggestionValues(true, items);
    }

    private List<String> getDriverClasses(List<org.talend.components.jdbc.common.Driver> driverJars)
            throws IOException {
        List<String> driverClasses = new ArrayList<>();

        List<String> paths = Optional.ofNullable(driverJars)
                .orElse(Collections.emptyList())
                .stream()
                .map(driver -> convertMvnPath2TckPath(driver.getPath()))
                .collect(Collectors.toList());

        List<URL> urls = resolver.resolveFromDescriptor(paths).stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException e) {
                log.warn(e.getMessage());
                return null;
            }
        }).collect(Collectors.toList());

        // TODO before this, should register mvn protocol for : new URL("mvn:foo/bar");
        // tck should already support that and provide some way to do that
        // but if not, we can use tcompv0 way
        try (URLClassLoader classLoader =
                new URLClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader())) {
            for (URL jarUrl : urls) {
                try (JarInputStream jarInputStream = new JarInputStream(jarUrl.openStream())) {
                    JarEntry nextJarEntry = jarInputStream.getNextJarEntry();
                    while (nextJarEntry != null) {
                        boolean isFile = !nextJarEntry.isDirectory();
                        if (isFile) {
                            String name = nextJarEntry.getName();
                            if (name != null && name.toLowerCase().endsWith(".class")) {
                                String className = changeFileNameToClassName(name);
                                try {
                                    Class clazz = classLoader.loadClass(className);
                                    if (Driver.class.isAssignableFrom(clazz)) {
                                        driverClasses.add(clazz.getName());
                                    }
                                } catch (Throwable th) {
                                    // ignore all the exceptions, especially the class not found exception when look up
                                    // a class
                                    // outside the jar
                                }
                            }
                        }

                        nextJarEntry = jarInputStream.getNextJarEntry();
                    }
                }
            }
        }

        if (driverClasses.isEmpty()) {
            throw new RuntimeException("Need to set jdbc jar or no jdbc driver class found in jdbc jar.");
        }

        return driverClasses;
    }

    private String changeFileNameToClassName(String name) {
        name = name.replace('/', '.');
        name = name.replace('\\', '.');
        name = name.substring(0, name.length() - 6);
        return name;
    }

    private String removeQuote(String content) {
        if (content.startsWith("\"") && content.endsWith("\"")) {
            return content.substring(1, content.length() - 1);
        }

        return content;
    }

    @HealthCheck("CheckConnection")
    public HealthCheckStatus validateBasicDataStore(@Option final JDBCDataStore dataStore) {
        try (final DataSourceWrapper dataSource = this.createConnection(dataStore, false);
                final Connection ignored = dataSource.getConnection()) {

        } catch (Exception e) {
            return new HealthCheckStatus(HealthCheckStatus.Status.KO, e.getMessage());
        }
        return new HealthCheckStatus(HealthCheckStatus.Status.OK, "Connection successful");
    }

    public static final String CONNECTION_POOL_KEY = "cpk";

    private static final String URL_KEY = "url";

    private static final String USERNAME_KEY = "username";

    public DataSourceWrapper createDataSource(final JDBCDataStore dataStore)
            throws SQLException {
        DataSourceWrapper dataSourceWrapper =
                createConnectionOrGetFromSharedConnectionPoolOrDataSource(dataStore, context, false);

        // not call this in HikariDataSource wrapper as worry HikariDataSource implement
        if (dataStore.isUseAutoCommit()) {
            dataSourceWrapper.getConnection().setAutoCommit(dataStore.isAutoCommit());
        }

        return dataSourceWrapper;
    }

    @CreateConnection
    public Connection createConnection(@Option("configuration") final JDBCDataStore dataStore)
            throws SQLException {
        DataSourceWrapper dataSourceWrapper = createDataSource(dataStore);

        context.set(CONNECTION_POOL_KEY, dataSourceWrapper);
        // some old javajet connectors reuse this jdbc connection connector
        context.setGlobal(URL_KEY + "_" + context.getConnectorId(), dataStore.getJdbcUrl());
        context.setGlobal(USERNAME_KEY + "_" + context.getConnectorId(), dataStore.getUserId());

        return dataSourceWrapper.getConnection();
    }

    @CloseConnection
    public CloseConnectionObject closeConnection() {
        return new CloseConnectionObject() {

            public boolean close() {
                Optional.ofNullable(this.getConnection())
                        .map(Connection.class::cast)
                        .ifPresent(conn -> {
                            try {
                                // as tjdbcconnection also works for studio javajet component like tjdbcscdelt and so
                                // on,
                                // so we can't pass DataSourceWrapper for closing data source directly in close
                                // component, have to pass java.sql.Connection,
                                // so here also have to pass connection pool object here for closing connection pool in
                                // close component
                                if (context == null) {
                                    if (conn != null && !conn.isClosed()) {
                                        conn.close();
                                    }
                                } else {
                                    DataSourceWrapper dataSourceWrapper =
                                            DataSourceWrapper.class.cast(context.get(CONNECTION_POOL_KEY));
                                    if (dataSourceWrapper != null) {
                                        dataSourceWrapper.close();
                                    } else if (conn != null && !conn.isClosed()) {
                                        conn.close();
                                    }
                                }
                            } catch (SQLException e) {
                                // TODO
                            }
                        });
                return true;
            }

        };
    }

    @Suggestions("FETCH_TABLES")
    public SuggestionValues fetchTables(@Option final JDBCDataStore dataStore) throws SQLException {
        final List<SuggestionValues.Item> items = new ArrayList<>();

        getSchemaNames(dataStore).stream().forEach(tableName -> {
            items.add(new SuggestionValues.Item(tableName, tableName));
        });

        return new SuggestionValues(true, items);
    }

    private List<String> getSchemaNames(final JDBCDataStore dataStore) throws SQLException {
        List<String> result = new ArrayList<>();
        try (final DataSourceWrapper dataSource = createConnection(dataStore, false);
                final Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMetaData = conn.getMetaData();

            Set<String> tableTypes = getAvailableTableTypes(dbMetaData);

            String database_schema = getDatabaseSchema(dataStore);
            if (database_schema == null) {
                // from jdbc api to get the runtime jdbc schema, but it may not be right?
                database_schema = getDatabaseSchema(conn);
            }

            try (ResultSet resultset =
                    dbMetaData.getTables(conn.getCatalog(), database_schema, null, tableTypes.toArray(new String[0]))) {
                while (resultset.next()) {
                    String tableName = resultset.getString("TABLE_NAME");
                    if (tableName == null) {
                        tableName = resultset.getString("SYNONYM_NAME");
                    }
                    result.add(tableName);
                }
            }
        } catch (SQLException e) {
            // TODO process it
            throw e;
        }
        return result;
    }

    /**
     * get database schema for database special
     *
     * @return
     */
    private String getDatabaseSchema(final JDBCDataStore dataStore) {
        String jdbc_url = dataStore.getJdbcUrl();
        String username = dataStore.getUserId();
        if (jdbc_url != null && username != null && jdbc_url.contains("oracle")) {
            return username.toUpperCase();
        }
        return null;
    }

    public static String getDatabaseSchema(Connection connection) throws SQLException {
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

    private Set<String> getAvailableTableTypes(DatabaseMetaData dbMetaData) throws SQLException {
        Set<String> availableTableTypes = new HashSet<String>();
        List<String> neededTableTypes = Arrays.asList("TABLE", "VIEW", "SYNONYM");

        try (ResultSet rsTableTypes = dbMetaData.getTableTypes()) {
            while (rsTableTypes.next()) {
                String currentTableType = rsTableTypes.getString("TABLE_TYPE");
                if (currentTableType == null) {
                    currentTableType = "";
                }
                currentTableType = currentTableType.trim();
                if ("BASE TABLE".equalsIgnoreCase(currentTableType)) {
                    currentTableType = "TABLE";
                }
                if (neededTableTypes.contains(currentTableType)) {
                    availableTableTypes.add(currentTableType);
                }
            }
        }

        return availableTableTypes;
    }

    private DataSourceWrapper createConnection(final JDBCDataStore dataStore, final boolean readonly)
            throws SQLException {
        return createConnection(dataStore, readonly, Collections.emptyMap());
    }

    private DataSourceWrapper createConnection(final JDBCDataStore dataStore, final boolean readonly,
            final Map<String, String> additionalJDBCProperties)
            throws SQLException {
        // TODO check this readonly before: conn = createConnection(dataStore, readonly);
        JDBCDataSource dataSource = new JDBCDataSource(this.resolver, dataStore, this, additionalJDBCProperties);
        Connection conn = dataSource.getConnection();
        // somebody add it for performance for dataprep
        if (readonly) {
            try {
                conn.setReadOnly(true);// TODO check why we get the value by "setting.isReadOnly()" before, here now
                // use "true" directly
            } catch (SQLException e) {
                log.debug("JDBC driver '{}' does not support read only mode.", dataStore.getJdbcClass(), e);
            }
        }

        return new DataSourceWrapper(dataSource, conn);
    }

    private static final String KEY_DB_DATASOURCES_RAW = "KEY_DB_DATASOURCES_RAW";

    public DataSourceWrapper createConnectionOrGetFromSharedConnectionPoolOrDataSource(final JDBCDataStore dataStore,
            final RuntimeContextHolder context, final boolean readonly) throws SQLException {
        return createConnectionOrGetFromSharedConnectionPoolOrDataSource(dataStore, context, readonly,
                Collections.emptyMap());
    }

    public DataSourceWrapper createConnectionOrGetFromSharedConnectionPoolOrDataSource(final JDBCDataStore dataStore,
            final RuntimeContextHolder context, final boolean readonly,
            final Map<String, String> additionalJDBCProperties) throws SQLException {
        Connection conn = null;
        log.debug("Connection attempt to '{}' with the username '{}'", dataStore.getJdbcUrl(), dataStore.getUserId());

        if (dataStore.isUseSharedDBConnection()) {
            if (dataStore.isUseDataSource()) {
                throw new RuntimeException(
                        "\"Use or register a shared DB Connection\" can't work with \"Specify a data source alias\" together, please uncheck one");
            }

            log.debug("Uses shared connection with name: '{}'", dataStore.getSharedDBConnectionName());
            log.debug("Connection URL: '{}', User name: '{}'", dataStore.getJdbcUrl(), dataStore.getUserId());
            try {
                // can't use interface as classloader issue, so use reflect here
                Method method = Class.forName("routines.system.SharedDBConnection")
                        .getMethod("getDBConnection", String.class, String.class, String.class, String.class,
                                String.class);
                conn = Connection.class.cast(method.invoke(null, dataStore.getJdbcClass(), dataStore.getJdbcUrl(),
                        dataStore.getUserId(),
                        dataStore.getPassword(), dataStore.getSharedDBConnectionName()));
                return new DataSourceWrapper(null, conn);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("class not found: " + e.getMessage());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("method not found: " + e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (dataStore.isUseDataSource()) {
            java.util.Map<String, DataSource> dataSources = (java.util.Map<String, javax.sql.DataSource>) context
                    .getGlobal(KEY_DB_DATASOURCES_RAW);
            if (dataSources != null) {
                DataSource datasource = dataSources.get(dataStore.getDataSourceAlias());
                if (datasource == null) {
                    throw new RuntimeException(
                            "No DataSource with alias: " + dataStore.getDataSourceAlias() + " available!");
                }
                conn = datasource.getConnection();
                if (conn == null) {
                    throw new RuntimeException("Unable to get a pooled database connection from pool");
                }

                return new DataSourceWrapper(null, conn);
            } else {
                return createConnection(dataStore, false);
            }
        } else {
            return createConnection(dataStore, readonly, additionalJDBCProperties);
        }
    }

    // studio will pass like this : mvn:mysql/mysql-connector-java/8.0.18/jar
    // but tck here expect like this : mysql:mysql-connector-java:jar:8.0.18
    private static String convertMvnPath2TckPath(String mvnPath) {
        if (mvnPath == null) {
            return null;
        }
        if (mvnPath.startsWith("mvn:")) {
            return mvnPath.substring(4, mvnPath.lastIndexOf('/')).replace('/', ':');
        }

        return mvnPath;
    }

    // HikariCP force to use java.sql.Connection.isValid method, but studio allow any jdbc driver which may not
    // implement that isValid method,
    // also we don't want to set different test query for different database, so here give a chance to choose to use
    // HikariCP or java jdbc api directly
    private static class ConnectionPool {

        private HikariDataSource dataSource;

        private java.sql.Connection connection;

        private void initConnectionPool(final JDBCDataStore dataStore, final List<String> driverPaths,
                final JDBCService jdbcService, final Map<String, String> additionalJDBCProperties) {
            dataSource = new HikariDataSource();

            DatabaseSpecial.doConfig4DifferentDatabaseAndDifferentRuntimeEnv(dataSource, dataStore, driverPaths,
                    jdbcService, additionalJDBCProperties);

            dataSource.setMaximumPoolSize(1);
        }

        private void initSingleConnection(final JDBCDataStore dataStore) {
            final String jdbcClass = dataStore.getJdbcClass();
            if (jdbcClass == null || jdbcClass.isEmpty()) {
                throw new RuntimeException("Please set jdbc driver class.");
            }
            final ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
            Driver driver;
            try {
                Class<?> driverClass = threadContextClassLoader.loadClass(jdbcClass);
                driver = (Driver) driverClass.newInstance();
            } catch (ClassNotFoundException e) {
                // ConfigurableClassLoader for any tck connector runtime, and for dynamic load jar way, the classloader
                // graph like this:
                // ConfigurableClassLoader(dynamic one)==>ConfigurableClassLoader(static root one)==>ClassLoader which
                // is decided by runtime env, like microservice/standalone==>System
                // for spring-boot microservice one, the .m2 setting don't contains jdbc jar, so
                // ConfigurableClassLoader(dynamic one) can't load it,
                // also the parent one(ConfigurableClassLoader(static root one)) can't too, but for the microservice, in
                // fact, jdbc jar in its classpath, so use the parent's parent
                ClassLoader cl = threadContextClassLoader.getParent();
                if (cl != null) {
                    cl = cl.getParent();

                    if (cl != null) {
                        try {
                            driver = (Driver) cl.loadClass(jdbcClass).newInstance();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            final Properties properties = new Properties();
            if (dataStore.getUserId() != null) {
                properties.setProperty("user", dataStore.getUserId());
            }
            if (dataStore.getPassword() != null) {
                properties.setProperty("password", dataStore.getPassword());
            }
            // TODO we need to disable allowLoadLocalInfile or allowLocalInfile for security for studio?
            // see https://jira.talendforge.org/browse/TDI-42006

            try {
                final String url = dataStore.getJdbcUrl();
                connection = driver.connect(url, properties);
                if (connection == null) {
                    throw new SQLException("No suitable driver found for " + url, "08001");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        ConnectionPool(final JDBCDataStore dataStore, final List<String> driverPaths, final boolean useConnectionPool,
                final JDBCService jdbcService, final Map<String, String> additionalJDBCProperties) {
            if (useConnectionPool) {
                initConnectionPool(dataStore, driverPaths, jdbcService, additionalJDBCProperties);
            } else {
                initSingleConnection(dataStore);
            }
        }

        java.sql.Connection getConnection() throws SQLException {
            if (connection != null) {
                return connection;
            } else {
                return dataSource.getConnection();
            }
        }

        void close() {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                dataSource.close();
            }
        }

    }

    public static class JDBCDataSource implements AutoCloseable {

        private final Resolver.ClassLoaderDescriptor classLoaderDescriptor;

        private final ConnectionPool connectionPool;

        private final boolean isCloud;

        public JDBCDataSource(final Resolver resolver,
                final JDBCDataStore dataStore, final JDBCService jdbcService,
                final Map<String, String> additionalJDBCProperties) {
            final Thread thread = Thread.currentThread();
            final ClassLoader prev = thread.getContextClassLoader();

            isCloud = RuntimeEnvUtil.isCloud(dataStore);
            final List<String> paths;
            if (isCloud) {
                final JDBCConfiguration.Driver driver = jdbcService.getPlatformService().getDriver(dataStore);
                paths = driver.getPaths();
                classLoaderDescriptor = resolver.mapDescriptorToClassLoader(paths);
                if (!classLoaderDescriptor.resolvedDependencies().containsAll(driver.getPaths())) {
                    String missingJars = driver
                            .getPaths()
                            .stream()
                            .filter(p -> !classLoaderDescriptor.resolvedDependencies().contains(p))
                            .collect(joining("\n"));
                    throw new IllegalStateException(jdbcService.getI18n().errorDriverLoad(driver.getId(), missingJars));
                }
            } else {
                final List<org.talend.components.jdbc.common.Driver> drivers = dataStore.getJdbcDriver();
                paths = Optional.ofNullable(drivers)
                        .orElse(Collections.emptyList())
                        .stream()
                        .map(driver -> convertMvnPath2TckPath(driver.getPath()))
                        .collect(Collectors.toList());
                classLoaderDescriptor = resolver.mapDescriptorToClassLoader(paths);
            }

            try {
                thread.setContextClassLoader(classLoaderDescriptor.asClassLoader());
                connectionPool = new ConnectionPool(dataStore, paths, isCloud, jdbcService, additionalJDBCProperties);
            } finally {
                thread.setContextClassLoader(prev);
            }
        }

        public Connection getConnection() throws SQLException {
            final Thread thread = Thread.currentThread();
            final ClassLoader prev = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoaderDescriptor.asClassLoader());
                if (isCloud) {
                    return wrap(classLoaderDescriptor.asClassLoader(), connectionPool.getConnection(),
                            Connection.class);
                } else {
                    return connectionPool.getConnection();
                }
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
                connectionPool.close();
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
            // in any studio case, the jdbc jar is in origin classpath always : local run, remote job server, remote
            // engine, microservice and so on.
            // so even no need to call thread.setContextClassLoader, but as we have passed all tests in tujs.
            // and find a bug : after call mysql special jdbc api : enableStreamingResults, not works, OOM appear, that
            // seems caused here.
            // so here do a safe fix : when studio case, not use dynamic proxy to reuse that classLoader with jdbc jars
            // for outside call.
            // TODO : not use tccl for studio case
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

    public static class DataSourceWrapper implements AutoCloseable {

        private JDBCDataSource dataSource;

        private Connection connection;

        public DataSourceWrapper(JDBCDataSource dataSource, Connection connection) {
            this.dataSource = dataSource;
            this.connection = connection;
        }

        public Connection getConnection() throws SQLException {
            if (connection != null) {
                return connection;
            }

            if (dataSource != null) {
                connection = dataSource.getConnection();
                return connection;
            }

            return null;
        }

        @Override
        public void close() throws SQLException {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            if (dataSource != null) {
                dataSource.close();
            }
        }

    }

    // TODO add it back when apply to cloud platform or adjust the order for search DiscoverSchema and
    // DiscoverSchemaExtended
    // @DiscoverSchema(value = "JDBCQueryDataSet")
    public Schema guessSchemaByQuery(@Option final JDBCQueryDataSet dataSet)
            throws SQLException, MalformedURLException {
        return guessSchemaByQuery(dataSet, null);
    }

    private Schema guessSchemaByQuery(final JDBCQueryDataSet dataSet, final DBType dbTypeInComponentSetting)
            throws SQLException, MalformedURLException {
        // TODO provide a way to get the mapping files in studio platform, also this should work for cloud platform
        // no this for cloud platform
        // now have to use system prop to get it, TODO studio should set it to component server jvm
        String mappingFileDirLocation = System.getProperty(CommonUtils.MAPPING_LOCATION);
        URL mappingFileDir = null;
        if (mappingFileDirLocation != null) {
            mappingFileDir = new URL(mappingFileDirLocation);
        }

        Dbms mapping = null;
        if (mappingFileDir != null) {
            mapping = CommonUtils.getMapping(mappingFileDir, dataSet.getDataStore(), null, dbTypeInComponentSetting,
                    this);
        } else {
            // use the connector nested mapping file
            mapping = CommonUtils.getMapping("/mappings", dataSet.getDataStore(), null,
                    dbTypeInComponentSetting, this);
        }

        try (final DataSourceWrapper dataSource = createConnection(dataSet.getDataStore(), false);
                final Connection conn = dataSource.getConnection()) {
            try (Statement statement = conn.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(dataSet.getSqlQuery())) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    return SchemaInferer.infer(recordBuilderFactory, metaData, mapping, false);
                }
            }
        }
    }

    // @DiscoverSchema(value = "JDBCTableDataSet")
    public Schema guessSchemaByTable(@Option final JDBCTableDataSet dataSet)
            throws SQLException, MalformedURLException {
        // TODO provide a way to get the mapping files in studio platform, also this should work for cloud platform
        // no this for cloud platform
        // now have to use system prop to get it, TODO studio should set it to component server jvm
        String mappingFileDirLocation = System.getProperty(CommonUtils.MAPPING_LOCATION);
        URL mappingFileDir = null;
        if (mappingFileDirLocation != null) {
            mappingFileDir = new URL(mappingFileDirLocation);
        }

        // TODO dbTypeInComponentSetting exist for tjdbcinput, how to pass it?
        DBType dbTypeInComponentSetting = null;

        Dbms mapping = null;
        if (mappingFileDir != null) {
            mapping = CommonUtils.getMapping(mappingFileDir, dataSet.getDataStore(), null, dbTypeInComponentSetting,
                    this);
        } else {
            // use the connector nested mapping file
            mapping = CommonUtils.getMapping("/mappings", dataSet.getDataStore(), null,
                    dbTypeInComponentSetting, this);
        }

        try (final DataSourceWrapper dataSource = createConnection(dataSet.getDataStore(), false);
                final Connection conn = dataSource.getConnection()) {
            JDBCTableMetadata tableMetadata = new JDBCTableMetadata();
            // TODO no need to set catalog/schema here? maybe an old studio jdbc connector bug?
            tableMetadata.setDatabaseMetaData(conn.getMetaData()).setTablename(dataSet.getTableName());

            Schema schema = SchemaInferer.infer(recordBuilderFactory, tableMetadata, mapping, false, false);

            return schema;
        }
    }

    @Suggestions("FETCH_COLUMN_NAMES")
    public SuggestionValues fetchColumnNames(JDBCDataStore dataStore, String tableName) {
        if (true)
            throw new RuntimeException("i am running");

        // TODO return SchemaInfo elements
        return null;
    }

    @Getter
    @AllArgsConstructor
    public static class ColumnInfo {

        private final String columnName;

        private final int sqlType;

        private final boolean isNullable;
    }

    private Schema.Entry.Builder withPrecision(Schema.Entry.Builder builder, boolean ignorePrecision, int precision) {
        if (ignorePrecision) {
            return builder;
        }

        builder.withProp(SchemaProperty.SIZE, String.valueOf(precision));

        return builder;
    }

    private Schema.Entry.Builder withScale(Schema.Entry.Builder builder, boolean ignoreScale, int scale) {
        if (ignoreScale) {
            return builder;
        }

        builder.withProp(SchemaProperty.SCALE, String.valueOf(scale));
        return builder;
    }

    // here use dataset name link for input
    @DiscoverSchemaExtended("JDBCQueryDataSet")
    public Schema discoverInputSchema(@Option("configuration") final JDBCInputConfig config)
            throws SQLException, MalformedURLException {
        Schema result = guessSchemaByQuery(config.getDataSet(),
                config.getConfig().isEnableMapping() ? config.getConfig().getMapping() : null);
        return result;
    }

    // here use component name link for processor
    @DiscoverSchemaExtended("Output")
    public Schema discoverOutputSchema(@Option("configuration") final JDBCOutputConfig config, final String branch)
            throws SQLException, MalformedURLException {
        Schema result = guessSchemaByTable(config.getDataSet());
        if ("reject".equalsIgnoreCase(branch)) {
            result = extendSchemaWithRejectColumns(result);
        }
        return result;
    }

    // here use component name link for processor
    @DiscoverSchemaExtended("Row")
    public Schema discoverRowSchema(final Schema incomingSchema, @Option("configuration") final JDBCRowConfig config,
            final String branch) {
        Schema result = incomingSchema != null ? incomingSchema
                : recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD).build();
        if ("reject".equalsIgnoreCase(branch)) {
            result = extendSchemaWithRejectColumns(result);
        }
        return result;
    }

    public Schema extendSchemaWithRejectColumns(Schema origin) {
        Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(origin);
        schemaBuilder.withEntry(recordBuilderFactory.newEntryBuilder()
                .withName("errorCode")
                .withType(Schema.Type.STRING)
                .withNullable(false)
                .withProp(SchemaProperty.SIZE, "255")
                .build());
        schemaBuilder.withEntry(recordBuilderFactory.newEntryBuilder()
                .withName("errorMessage")
                .withType(Schema.Type.STRING)
                .withNullable(false)
                .withProp(SchemaProperty.SIZE, "255")
                .build());
        return schemaBuilder.build();
    }

    @AsyncValidation(value = "ACTION_VALIDATE_SORT_KEYS")
    public ValidationResult validateSortKeys(final RedshiftSortStrategy sortStrategy, final List<String> sortKeys) {
        if (RedshiftSortStrategy.SINGLE.equals(sortStrategy) && sortKeys != null && sortKeys.size() > 1) {
            return new ValidationResult(ValidationResult.Status.KO, i18n.errorSingleSortKeyInvalid());
        }

        return new ValidationResult(ValidationResult.Status.OK, "");
    }

}
