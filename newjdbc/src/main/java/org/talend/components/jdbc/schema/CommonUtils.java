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
package org.talend.components.jdbc.schema;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.DBType;
import org.talend.components.jdbc.common.Driver;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.platforms.RuntimeEnvUtil;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Schema;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

// TODO better to extract this to a new util project as that's used in tcompv0 jdbc too
@Slf4j
public class CommonUtils {

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

    /**
     * computer the real database type by driver jar and class, this is useful for the tjdbcxxx
     *
     * @param dataStore
     * @param dbType
     * @return
     */
    private static String getRealDBType(JDBCDataStore dataStore, String dbType, JDBCService jdbcService) {
        if (dbType == null || dbType.equals(EDatabaseTypeName.GENERAL_JDBC.getDisplayName())) {
            final boolean isCloud = RuntimeEnvUtil.isCloud(dataStore);

            String driverClassName;
            final List<String> driverPaths;
            if (isCloud) {
                JDBCConfiguration.Driver driverInfo = jdbcService.getPlatformService().getDriver(dataStore);
                driverClassName = driverInfo.getClassName();
                driverPaths = driverInfo.getPaths();
            } else {
                driverClassName = dataStore.getJdbcClass();

                if ("com.sybase.jdbc3.jdbc.SybDataSource".equals(driverClassName)) {
                    driverClassName = EDatabase4DriverClassName.SYBASEASE.getDriverClass();
                }

                driverPaths =
                        dataStore.getJdbcDriver().stream().map(Driver::getPath).collect(Collectors.toList());
            }

            final StringBuilder sb = new StringBuilder();
            for (String path : driverPaths) {
                sb.append(path);
            }
            final String driverJarInfo = sb.toString();

            dbType = getDbTypeByClassNameAndDriverJar(driverClassName, driverJarInfo);

            if (dbType == null) {
                // if we can not get the DB Type from the existing driver list, just set back the type to ORACLE
                // since it's one DB unknown from Talend.
                // it might not work directly for all DB, but it will generate a standard query.
                dbType = EDatabaseTypeName.ORACLE_OCI.getDisplayName();
            }
        }
        return dbType;
    }

    // connector job runtime key
    public static final String MAPPING_URL_SUBFIX = "MAPPINGS_URL";

    // studio button trigger key
    public static final String MAPPING_LOCATION = "MAPPING_LOCATION";

    public static Dbms getMapping(String mappingFilesDir, JDBCDataStore dataStore,
            String dbTypeByComponentType,
            DBType dbTypeInComponentSetting, JDBCService jdbcService) {
        String mappingFileSubfix =
                guessMappingFile(dataStore, dbTypeByComponentType, dbTypeInComponentSetting, jdbcService);

        InputStream is = CommonUtils.class
                .getResourceAsStream(String.format("%s/mapping_%s.xml", mappingFilesDir, mappingFileSubfix));
        if (is == null) {
            is = CommonUtils.class.getResourceAsStream(
                    String.format("%s/mapping_%s.xml", mappingFilesDir, mappingFileSubfix.toLowerCase()));
        }

        if (is == null) {
            return null;
        }

        MappingFileLoader fileLoader = new MappingFileLoader();
        Dbms dbms = fileLoader.load(is).get(0);

        return dbms;
    }

    public static Dbms getMapping(URL mappingFilesDir, JDBCDataStore dataStore,
            String dbTypeByComponentType/*
                                         * for example, if tjdbcxxx, the type is "General JDBC", if tmysqlxxx, the type
                                         * is "MySQL"
                                         */,
            DBType dbTypeInComponentSetting/* in tjdbcinput, can choose the db type in advanced setting */,
            JDBCService jdbcService) {
        String mappingFileSubfix =
                guessMappingFile(dataStore, dbTypeByComponentType, dbTypeInComponentSetting, jdbcService);

        MappingFileLoader fileLoader = new MappingFileLoader();
        Dbms dbms = null;

        try {
            dbms = loadFromStream(fileLoader, mappingFilesDir, mappingFileSubfix);
        } catch (Exception e) {
            // Fallback to old solution
            log.warn("Couldn't load mapping from stream. Trying to read as File.", e);
            dbms = loadFromFile(fileLoader, mappingFilesDir, mappingFileSubfix);
        }

        return dbms;
    }

    private static String guessMappingFile(JDBCDataStore dataStore, String dbTypeByComponentType,
            DBType dbTypeInComponentSetting, JDBCService jdbcService) {
        final String realDbType = getRealDBType(dataStore, dbTypeByComponentType, jdbcService);
        final String product = EDatabaseTypeName.getTypeFromDisplayName(realDbType).getProduct();

        String mappingFileSubfix = productValue2DefaultMappingFileSubfix.get(product);

        if ((dbTypeInComponentSetting != null) && (mappingFileSubfix == null)) {
            mappingFileSubfix = dbType2MappingFileSubfix.get(dbTypeInComponentSetting);
        }

        if (mappingFileSubfix == null) {
            mappingFileSubfix = "Mysql";
        }
        return mappingFileSubfix;
    }

    private static Dbms loadFromFile(final MappingFileLoader fileLoader, final URL mappingFilesDir,
            final String mappingFileSubfix) {
        if (mappingFilesDir == null) {
            return null;
        }

        File mappingFileFullPath = new File(mappingFilesDir.getFile(), "mapping_" + mappingFileSubfix + ".xml");
        if (!mappingFileFullPath.exists()) {
            mappingFileFullPath =
                    new File(mappingFilesDir.getFile(), "mapping_" + mappingFileSubfix.toLowerCase() + ".xml");
        }
        return fileLoader.load(mappingFileFullPath).get(0);
    }

    private static Dbms loadFromStream(final MappingFileLoader fileLoader, final URL mappingFilesDir,
            final String mappingFileSubfix)
            throws IOException, URISyntaxException {
        Dbms dbms = null;
        InputStream mappingStream = null;
        try {
            mappingStream = getStream(mappingFilesDir, mappingFileSubfix);
            dbms = fileLoader.load(mappingStream).get(0);
        } finally {
            if (mappingStream != null) {
                try {
                    mappingStream.close();
                } catch (IOException e) {
                    log.warn("Couldn't close stream.", e);
                }
            }
        }
        return dbms;
    }

    private static InputStream getStream(final URL mappingFileDir, final String mappingFileSubfix)
            throws IOException, URISyntaxException {
        if (mappingFileDir == null) {
            throw new IllegalArgumentException("Mapping file directory URL cannot be null!");
        }
        URL mappingFileDirUrl = mappingFileDir;
        InputStream mappingStream = null;
        if (!mappingFileDirUrl.toString().endsWith("/")) {
            mappingFileDirUrl = new URL(mappingFileDirUrl.toString() + "/");
        }
        try {
            URL mappingFileFullUrl = mappingFileDirUrl.toURI().resolve("mapping_" + mappingFileSubfix + ".xml").toURL();
            mappingStream = mappingFileFullUrl.openStream();
        } catch (URISyntaxException | IOException e) {
            URL mappingFileFullUrl =
                    mappingFileDirUrl.toURI().resolve("mapping_" + mappingFileSubfix.toLowerCase() + ".xml").toURL();
            mappingStream = mappingFileFullUrl.openStream();
        }
        return mappingStream;
    }

    // now we use a inside mapping to do the mapping file search, not good and easy to break, TODO should load all the
    // mapping
    // files to memory only once, and search by the memory object
    private static Map<String, String> productValue2DefaultMappingFileSubfix = new HashMap<>();

    private static Map<DBType, String> dbType2MappingFileSubfix = new HashMap<>();

    static {
        dbType2MappingFileSubfix.put(DBType.AS400, "AS400");
        dbType2MappingFileSubfix.put(DBType.ACCESS, "Access");
        dbType2MappingFileSubfix.put(DBType.DB2, "IBMDB2");
        dbType2MappingFileSubfix.put(DBType.FIREBIRD, "Firebird");
        dbType2MappingFileSubfix.put(DBType.HSQLDB, "HSQLDB");
        dbType2MappingFileSubfix.put(DBType.INFORMIX, "Informix");
        dbType2MappingFileSubfix.put(DBType.INGRES, "Ingres");
        dbType2MappingFileSubfix.put(DBType.VECTORWISE, "VectorWise");
        dbType2MappingFileSubfix.put(DBType.INTERBASE, "Interbase");
        dbType2MappingFileSubfix.put(DBType.JAVADB, "JavaDB");
        dbType2MappingFileSubfix.put(DBType.MAXDB, "MaxDB");
        dbType2MappingFileSubfix.put(DBType.MSSQL, "MSSQL");
        dbType2MappingFileSubfix.put(DBType.MYSQL, "Mysql");
        dbType2MappingFileSubfix.put(DBType.NETEZZA, "Netezza");
        dbType2MappingFileSubfix.put(DBType.ORACLE, "Oracle");
        dbType2MappingFileSubfix.put(DBType.POSTGRESQL, "Postgres");
        dbType2MappingFileSubfix.put(DBType.POSTGREPLUS, "PostgresPlus");
        dbType2MappingFileSubfix.put(DBType.SQLITE, "SQLite");
        dbType2MappingFileSubfix.put(DBType.SYBASE, "Sybase");
        dbType2MappingFileSubfix.put(DBType.SAPHANA, "SAPHana");
        dbType2MappingFileSubfix.put(DBType.TERADATA, "Teradata");
        dbType2MappingFileSubfix.put(DBType.VERTICA, "Vertica");
        dbType2MappingFileSubfix.put(DBType.H2, "H2");
        dbType2MappingFileSubfix.put(DBType.ODBC, "MSODBC");
    }

    static {
        productValue2DefaultMappingFileSubfix.put("ACCESS", "Access");
        productValue2DefaultMappingFileSubfix.put("AS400", "AS400");
        productValue2DefaultMappingFileSubfix.put("BIGQUERY", "BigQuery");
        productValue2DefaultMappingFileSubfix.put("Cassandra", "Cassandra");
        // productValue2DefaultMappingFileSubfix.put("Cassandra", "Cassandra_datastax");
        // productValue2DefaultMappingFileSubfix.put("Cassandra", "Cassandra22_datastax");
        productValue2DefaultMappingFileSubfix.put("Exasol", "Exasol");
        productValue2DefaultMappingFileSubfix.put("FIREBIRD", "Firebird");
        productValue2DefaultMappingFileSubfix.put("GREENPLUM", "Greenplum");
        productValue2DefaultMappingFileSubfix.put("H2", "H2");
        productValue2DefaultMappingFileSubfix.put("HIVE", "Hive");
        productValue2DefaultMappingFileSubfix.put("HSQLDB", "HSQLDB");
        productValue2DefaultMappingFileSubfix.put("IBM_DB2", "IBMDB2");
        productValue2DefaultMappingFileSubfix.put("IMPALA", "Impala");
        productValue2DefaultMappingFileSubfix.put("INFORMIX", "Informix");
        productValue2DefaultMappingFileSubfix.put("INGRES", "Ingres");
        productValue2DefaultMappingFileSubfix.put("INTERBASE", "Interbase");
        productValue2DefaultMappingFileSubfix.put("JAVADB", "JavaDB");
        productValue2DefaultMappingFileSubfix.put("MAXDB", "MaxDB");
        productValue2DefaultMappingFileSubfix.put("ODBC", "MsOdbc");
        productValue2DefaultMappingFileSubfix.put("SQL_SERVER", "MSSQL");
        productValue2DefaultMappingFileSubfix.put("MYSQL", "Mysql");
        productValue2DefaultMappingFileSubfix.put("NETEZZA", "Netezza");
        productValue2DefaultMappingFileSubfix.put("ORACLE", "Oracle");
        productValue2DefaultMappingFileSubfix.put("PARACCEL", "ParAccel");
        productValue2DefaultMappingFileSubfix.put("POSTGRESQL", "Postgres");
        productValue2DefaultMappingFileSubfix.put("POSTGRESPLUS", "PostgresPlus");
        productValue2DefaultMappingFileSubfix.put("REDSHIFT", "Redshift");
        productValue2DefaultMappingFileSubfix.put("SAPHANA", "SAPHana");
        productValue2DefaultMappingFileSubfix.put("SNOWFLAKE", "Snowflake");
        productValue2DefaultMappingFileSubfix.put("SQLITE", "SQLite");
        productValue2DefaultMappingFileSubfix.put("SYBASE", "Sybase");
        productValue2DefaultMappingFileSubfix.put("TERADATA", "Teradata");
        productValue2DefaultMappingFileSubfix.put("VECTORWISE", "VectorWise");
        productValue2DefaultMappingFileSubfix.put("VERTICA", "Vertica");

        productValue2DefaultMappingFileSubfix.put("SNOWFLAKE", "Snowflake");
        productValue2DefaultMappingFileSubfix.put("DELTALAKE", "Databricks_Delta_Lake");
    }

    // hywang add for bug 7575
    private static String getDbTypeByClassNameAndDriverJar(String driverClassName, String driverJar) {
        List<EDatabase4DriverClassName> t4d = EDatabase4DriverClassName.indexOfByDriverClass(driverClassName);
        if (t4d.size() == 1) {
            return t4d.get(0).getDbTypeName();
        } else if (t4d.size() > 1) {
            // for some dbs use the same driverClassName.
            if (driverJar == null || "".equals(driverJar) || !driverJar.contains(".jar")) {
                return t4d.get(0).getDbTypeName();
            } else if (driverJar.contains("postgresql-8.3-603.jdbc3.jar")
                    || driverJar.contains("postgresql-8.3-603.jdbc4.jar")
                    || driverJar.contains("postgresql-8.3-603.jdbc2.jar")) {
                return EDatabase4DriverClassName.PSQL.getDbTypeName();
            } else {
                return t4d.get(0).getDbTypeName(); // first default
            }
        }
        return null;
    }

    public static Schema.Entry getField(Schema schema, String fieldName) {
        if (schema == null) {
            return null;
        }

        for (Schema.Entry outField : schema.getEntries()) {
            if (outField.getName().equals(fieldName)) {
                return outField;
            }
        }

        return null;
    }

    public static int getFieldIndex(Schema schema, String fieldName) {
        if (schema == null) {
            return -1;
        }

        int index = 0;
        for (Schema.Entry outField : schema.getEntries()) {
            if (outField.getName().equals(fieldName)) {
                return index;
            }

            index++;
        }

        return index;
    }

}
