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

/**
 * qzhang class global comment. Detailled comment <br/>
 * 
 */
public enum EDatabaseTypeName {

    MYSQL(
            "MySQL", //$NON-NLS-1$
            "MySQL", //$NON-NLS-1$
            Boolean.FALSE,
            "MYSQL", //$NON-NLS-1$
            "MYSQL", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    AMAZON_AURORA(
            "Amazon Aurora", //$NON-NLS-1$
            "Amazon Aurora", //$NON-NLS-1$
            Boolean.FALSE,
            "AMAZON_AURORA", //$NON-NLS-1$
            "AMAZON_AURORA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    PSQL(
            "PostgreSQL", //$NON-NLS-1$
            "PostgreSQL", //$NON-NLS-1$
            Boolean.TRUE,
            "POSTGRESQL", //$NON-NLS-1$
            "POSTGRE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    PLUSPSQL(
            "PostgresPlus", //$NON-NLS-1$
            "PostgresPlus", //$NON-NLS-1$
            Boolean.TRUE,
            "POSTGRESPLUS", //$NON-NLS-1$
            "POSTGREPLUS", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    ORACLEFORSID(
            "ORACLE_SID", //$NON-NLS-1$
            "Oracle with SID", //$NON-NLS-1$
            Boolean.TRUE,
            "ORACLE", //$NON-NLS-1$
            "DBORACLE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    ORACLESN(
            "ORACLE_SERVICE_NAME", //$NON-NLS-1$
            "Oracle with service name", //$NON-NLS-1$
            Boolean.TRUE,
            "ORACLE", //$NON-NLS-1$
            "DBORACLE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    ORACLE_OCI(
            "ORACLE_OCI", //$NON-NLS-1$
            "Oracle OCI", //$NON-NLS-1$
            Boolean.TRUE,
            "ORACLE", //$NON-NLS-1$
            "DBORACLE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    ORACLE_CUSTOM(
            "ORACLE_CUSTOM", //$NON-NLS-1$
            "Oracle Custom", //$NON-NLS-1$
            Boolean.TRUE,
            "ORACLE", //$NON-NLS-1$
            "DBORACLE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    /**
     * @deprecated odbc is not supported in java8
     */
    GODBC(
            "Generic ODBC", //$NON-NLS-1$
            "Generic ODBC (Unsupported)", //$NON-NLS-1$
            Boolean.FALSE,
            "ODBC", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    /**
     * @deprecated odbc is not supported in java8
     */
    MSODBC(
            "Microsoft SQL (Odbc driver)", //$NON-NLS-1$
            "Microsoft SQL Server (Odbc driver, Unsupported)", //$NON-NLS-1$
            Boolean.FALSE,
            "ODBC", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    IBMDB2(
            "IBM DB2", //$NON-NLS-1$
            "IBM DB2", //$NON-NLS-1$
            Boolean.TRUE,
            "IBM_DB2", //$NON-NLS-1$
            "DB2", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    IBMDB2ZOS(
            "IBM DB2 ZOS", //$NON-NLS-1$
            "IBM DB2 ZOS", //$NON-NLS-1$
            Boolean.TRUE,
            "IBM_DB2", //$NON-NLS-1$
            "DB2", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    SYBASEASE(
            "SybaseASE", //$NON-NLS-1$
            "Sybase (ASE and IQ)", //$NON-NLS-1$
            Boolean.TRUE,
            "SYBASE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    // this Sybase IQ not used.
    SYBASEIQ(
            "Sybase IQ", //$NON-NLS-1$
            "Sybase IQ", //$NON-NLS-1$
            Boolean.TRUE,
            "SYBASE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    MSSQL(
            "MSSQL", //$NON-NLS-1$
            "Microsoft SQL Server", //$NON-NLS-1$
            Boolean.TRUE,
            "SQL_SERVER", //$NON-NLS-1$
            "MSSQL", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    MSSQL05_08(
            "MSSQL", //$NON-NLS-1$
            "Microsoft SQL Server 2005/2008", //$NON-NLS-1$
            Boolean.TRUE,
            "SQL_SERVER", //$NON-NLS-1$
            "MSSQL", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    // this don't use in Branch 2.0
    HSQLDB(
            "HSQLDB", //$NON-NLS-1$
            "HSQLDB", //$NON-NLS-1$
            Boolean.FALSE,
            "HSQLDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    HSQLDB_SERVER(
            "HSQLDB Server", //$NON-NLS-1$
            "HSQLDB Server", //$NON-NLS-1$
            Boolean.FALSE,
            "HSQLDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    HSQLDB_WEBSERVER(
            "HSQLDB WebServer", //$NON-NLS-1$
            "HSQLDB WebServer", //$NON-NLS-1$
            Boolean.FALSE,
            "HSQLDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    HSQLDB_IN_PROGRESS(
            "HSQLDB In-Process", //$NON-NLS-1$
            "HSQLDB In-Process", //$NON-NLS-1$
            Boolean.FALSE,
            "HSQLDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    JAVADB(
            "JavaDB", //$NON-NLS-1$
            "JavaDB", //$NON-NLS-1$
            Boolean.FALSE,
            "JAVADB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    INGRES(
            "Ingres", //$NON-NLS-1$
            "Ingres", //$NON-NLS-1$
            Boolean.FALSE,
            "INGRES", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema), // "INGRES"),
    INTERBASE(
            "Interbase", //$NON-NLS-1$
            "Interbase", //$NON-NLS-1$
            Boolean.FALSE,
            "INTERBASE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None), // "INTERBASE"),
    SQLITE(
            "SQLite", //$NON-NLS-1$
            "SQLite", //$NON-NLS-1$
            Boolean.FALSE,
            "SQLITE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None), // "SQLITE"),
    FIREBIRD(
            "FireBird", //$NON-NLS-1$
            "FireBird", //$NON-NLS-1$
            Boolean.FALSE,
            "FIREBIRD", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None), // "FIREBIRD"),
    INFORMIX(
            "Informix", //$NON-NLS-1$
            "Informix", //$NON-NLS-1$
            Boolean.TRUE,
            "INFORMIX", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None), // "INFORMIX");
    VECTORWISE(
            "VectorWise", //$NON-NLS-1$
            "VectorWise", //$NON-NLS-1$
            Boolean.FALSE,
            "VECTORWISE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    ACCESS(
            "Access", //$NON-NLS-1$
            "Access", //$NON-NLS-1$
            Boolean.FALSE,
            "ACCESS", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Default_Name), // "ACCESS");
    TERADATA(
            "Teradata", //$NON-NLS-1$
            "Teradata", //$NON-NLS-1$
            Boolean.TRUE,
            "TERADATA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema), // "TERADATA");
    AS400(
            "AS400", //$NON-NLS-1$
            "AS400", //$NON-NLS-1$
            Boolean.FALSE,
            "AS400", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Login),

    JAVADB_EMBEDED(
            "JavaDB Embeded", //$NON-NLS-1$
            "JavaDB Embeded", //$NON-NLS-1$
            Boolean.FALSE,
            "JAVADB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    JAVADB_JCCJDBC(
            "JavaDB JCCJDBC", //$NON-NLS-1$
            "JavaDB JCCJDBC", //$NON-NLS-1$
            Boolean.FALSE,
            "JAVADB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    JAVADB_DERBYCLIENT(
            "JavaDB DerbyClient", //$NON-NLS-1$
            "JavaDB DerbyClient", //$NON-NLS-1$
            Boolean.FALSE,
            "JAVADB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    VERTICA(
            "Vertica", //$NON-NLS-1$
            "Vertica", //$NON-NLS-1$
            Boolean.TRUE,
            "VERTICA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),

    MAXDB(
            "MAXDB", //$NON-NLS-1$
            "MaxDB", //$NON-NLS-1$
            Boolean.FALSE,
            "MAXDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    GREENPLUM(
            "Greenplum", //$NON-NLS-1$
            "Greenplum", //$NON-NLS-1$
            Boolean.TRUE,
            "GREENPLUM", //$NON-NLS-1$
            "GREENPLUM", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    PARACCEL(
            "ParAccel", //$NON-NLS-1$
            "ParAccel", //$NON-NLS-1$
            Boolean.TRUE,
            "PARACCEL", //$NON-NLS-1$
            "PARACCEL", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),
    NETEZZA(
            "Netezza", //$NON-NLS-1$
            "Netezza", //$NON-NLS-1$
            Boolean.FALSE,
            "NETEZZA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    SAS(
            "SAS", //$NON-NLS-1$
            "SAS", //$NON-NLS-1$
            Boolean.TRUE,
            "SAS", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),
    // General JDBC not support schema defalut
    GENERAL_JDBC(
            "General JDBC", //$NON-NLS-1$
            "General JDBC", //$NON-NLS-1$
            Boolean.FALSE,
            "JDBC", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),
    EXASOL(
            "Exasol", //$NON-NLS-1$
            "Exasol", //$NON-NLS-1$
            Boolean.TRUE,
            "Exasol", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    HIVE(
            "Hive", //$NON-NLS-1$
            "Hive", //$NON-NLS-1$
            Boolean.FALSE,
            "HIVE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.None),

    SAPHana(
            "SAPHana", //$NON-NLS-1$
            "SAPHana", //$NON-NLS-1$
            Boolean.TRUE,
            "SAPHANA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),

    H2("H2", "H2", Boolean.FALSE, "H2", EDatabaseSchemaOrCatalogMapping.Sid, EDatabaseSchemaOrCatalogMapping.None), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    REDSHIFT(
            "Redshift", //$NON-NLS-1$
            "Redshift", //$NON-NLS-1$
            Boolean.TRUE,
            "REDSHIFT", //$NON-NLS-1$
            "REDSHIFT", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Schema),

    IMPALA(
            "IMPALA", //$NON-NLS-1$
            "Impala", //$NON-NLS-1$
            Boolean.TRUE,
            "IMPALA", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.None,
            EDatabaseSchemaOrCatalogMapping.Schema),

    HBASE(
            "HBase", //$NON-NLS-1$
            "HBase", //$NON-NLS-1$
            Boolean.FALSE,
            "HBASE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Column_Family,
            true),

    MAPRDB(
            "MapRDB", //$NON-NLS-1$
            "MapRDB", //$NON-NLS-1$
            Boolean.FALSE,
            "MAPRDB", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Column_Family,
            true),

    SNOWFLAKE(
            "Snowflake", //$NON-NLS-1$
            "Snowflake", //$NON-NLS-1$
            Boolean.FALSE,
            "SNOWFLAKE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Column_Family,
            true),

    DELTALAKE(
            "Deltalake", //$NON-NLS-1$
            "Deltalake", //$NON-NLS-1$
            Boolean.FALSE,
            "DELTALAKE", //$NON-NLS-1$
            EDatabaseSchemaOrCatalogMapping.Sid,
            EDatabaseSchemaOrCatalogMapping.Column_Family,
            true);

    // displayName is used in Java code.
    private String displayName;

    private Boolean isNeedSchema;

    // dbType is used in compnonent XML file.
    private String dbType;

    // product used for the mappings.
    private String product;

    // needs a mapping for bug 0004305
    private String xmlType;

    private boolean useProvider = false;

    private EDatabaseSchemaOrCatalogMapping catalogMappingField;

    private EDatabaseSchemaOrCatalogMapping schemaMappingField;

    public EDatabaseSchemaOrCatalogMapping getCatalogMappingField() {
        return this.catalogMappingField;
    }

    public EDatabaseSchemaOrCatalogMapping getSchemaMappingField() {
        return this.schemaMappingField;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Boolean isNeedSchema() {
        return this.isNeedSchema;
    }

    public String getXmlName() {
        return this.dbType;
    }

    public String getProduct() {
        return this.product;
    }

    public String getXMLType() {
        return this.xmlType;
    }

    EDatabaseTypeName(String dbType, String displayName, Boolean isNeedSchema, String product,
            EDatabaseSchemaOrCatalogMapping catalogMappingField, EDatabaseSchemaOrCatalogMapping schemaMappingField) {
        this.displayName = displayName;
        this.isNeedSchema = isNeedSchema;
        this.dbType = dbType;
        this.product = product;
        this.xmlType = product;
        this.catalogMappingField = catalogMappingField;
        this.schemaMappingField = schemaMappingField;
    }

    EDatabaseTypeName(String dbType, String displayName, Boolean isNeedSchema, String product,
            EDatabaseSchemaOrCatalogMapping catalogMappingField, EDatabaseSchemaOrCatalogMapping schemaMappingField,
            boolean useProvider) {
        this(dbType, displayName, isNeedSchema, product, catalogMappingField, schemaMappingField);
        this.useProvider = useProvider;
    }

    EDatabaseTypeName(String dbType, String displayName, Boolean isNeedSchema, String product, String xmlType,
            EDatabaseSchemaOrCatalogMapping catalogMappingField, EDatabaseSchemaOrCatalogMapping schemaMappingField) {
        this.displayName = displayName;
        this.isNeedSchema = isNeedSchema;
        this.dbType = dbType;
        this.product = product;
        this.xmlType = xmlType;
        this.catalogMappingField = catalogMappingField;
        this.schemaMappingField = schemaMappingField;
    }

    public static EDatabaseTypeName getTypeFromDbType(String dbType) {
        if (dbType == null) {
            return getTypeFromDispalyNameOriginal(null);
        }
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getXmlName().equalsIgnoreCase(dbType)) {
                return typename;
            }
            if (typename.getProduct().equalsIgnoreCase(dbType)) {
                return typename;
            }
        }
        return getTypeFromDispalyNameOriginal(dbType);
    }

    public static EDatabaseTypeName getTypeFromDbType(String dbType, boolean isDefault) {
        if (dbType == null) {
            return getTypeFromDisplayNameOriginal(null, isDefault);
        }
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getXmlName().equalsIgnoreCase(dbType)) {
                return typename;
            }
            if (typename.getProduct().equalsIgnoreCase(dbType)) {
                return typename;
            }
        }
        return getTypeFromDisplayNameOriginal(dbType, isDefault);
    }

    public static EDatabaseTypeName getTypeFromDisplayName(String displayName) {
        return getTypeFromDbType(displayName);
    }

    private static EDatabaseTypeName getTypeFromDispalyNameOriginal(String displayName) {
        if (displayName == null) {
            return MYSQL;
        }
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getDisplayName().equalsIgnoreCase(displayName)) {
                return typename;
            }
        }
        return MYSQL;
    }

    public static EDatabaseTypeName getTypeFromDisplayName(String displayName, boolean isDefault) {
        return getTypeFromDisplayNameOriginal(displayName, isDefault);
    }

    private static EDatabaseTypeName getTypeFromDisplayNameOriginal(String displayName, boolean isDefault) {
        if (displayName == null && isDefault) {
            return MYSQL;
        } else if (displayName == null) {
            return null;
        }
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getDisplayName().equalsIgnoreCase(displayName)) {
                return typename;
            }
        }
        return isDefault ? MYSQL : null;
    }

    /**
     * DOC zli Comment method "getTypeFromProductName".
     * 
     * @param productName
     * @return
     */
    public static EDatabaseTypeName getTypeFromProductName(String productName) {
        if (productName == null) {
            return MYSQL;
        }
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getProduct().equals(productName)) {
                return typename;
            }
        }
        return MYSQL;
    }

    /**
     * This is only for the component type, not for the repository.
     * 
     * @param dbType
     * @return
     */
    public static boolean supportDbType(String dbType) {
        for (EDatabaseTypeName typename : EDatabaseTypeName.values()) {
            if (typename.getXmlName().equals(dbType)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUseProvider() {
        return useProvider;
    }

    /*
     * public boolean isSupport() {
     * boolean isSupport = true;
     * 
     * if (EDatabaseTypeName.GODBC == this || EDatabaseTypeName.MSODBC == this) {
     * boolean isSupportODBC = CoreRuntimePlugin.getInstance().getProjectPreferenceManager()
     * .getBoolean(IProjectSettingPreferenceConstants.METADATA_DBCONNECTION_ODBC_ENABLE);
     * isSupport = isSupportODBC;
     * }
     * 
     * return isSupport;
     * }
     */
}
