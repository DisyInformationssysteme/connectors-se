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

import java.sql.DatabaseMetaData;

/**
 * a model which provide all the information to fetch the meta data from a database table
 *
 */
public class JDBCTableMetadata {

    /**
     * database cataglog
     */
    private String catalog;

    /**
     * database schema
     */
    private String dbSchema;

    /**
     * table name
     */
    private String tablename;

    /**
     * JDBC DatabaseMetaData object, the entrance to access the JDBC API
     */
    private DatabaseMetaData DatabaseMetaData;

    public String getCatalog() {
        return catalog;
    }

    public JDBCTableMetadata setCatalog(String catalog) {
        this.catalog = catalog;
        return this;
    }

    public String getDbSchema() {
        return dbSchema;
    }

    public JDBCTableMetadata setDbSchema(String dbSchema) {
        this.dbSchema = dbSchema;
        return this;
    }

    public String getTablename() {
        return tablename;
    }

    public JDBCTableMetadata setTablename(String tablename) {
        this.tablename = tablename;
        return this;
    }

    public DatabaseMetaData getDatabaseMetaData() {
        return DatabaseMetaData;
    }

    public JDBCTableMetadata setDatabaseMetaData(DatabaseMetaData databaseMetaData) {
        DatabaseMetaData = databaseMetaData;
        return this;
    }

}
