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
 * DOC hywang.this enum is used to specific the relationship between schema and catalog and how they get their name from
 */
public enum EDatabaseSchemaOrCatalogMapping {
    // this kind of database do not need schema/catalog
    None,
    // don't know if refing to schema or catalog? represent the use the valuse of Connection.getSID()
    Sid,
    // schema definition : represent the use the valuse of Connection.getUiSchema()
    Schema,
    // catalog or schema definition : represent the use the valuse of Connection.getUserName() ,this is only used for
    // as400.
    Login,
    // for hbase,column family can be considered as the schema of the db
    Column_Family,
    // schema definition : use the connection.getName()
    Default_Name
    // this is for databases like micosoft access which didn't require the schema or catalog
}
