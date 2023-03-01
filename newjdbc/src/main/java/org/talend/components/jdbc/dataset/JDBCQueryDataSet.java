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
package org.talend.components.jdbc.dataset;

import lombok.Data;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Code;
import org.talend.sdk.component.api.configuration.ui.widget.Structure;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.List;

@Data
@GridLayout({
        @GridLayout.Row("schema"),
        @GridLayout.Row("dataStore"),
        @GridLayout.Row("tableName"),
        @GridLayout.Row("sqlQuery")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("dataStore")
})
@DataSet("JDBCQueryDataSet")
@Documentation("A query dataset")
public class JDBCQueryDataSet implements BaseDataSet, Serializable {

    @Option
    @Documentation("The connection information to execute")
    private JDBCDataStore dataStore;

    @Option
    @Suggestable(value = "FETCH_TABLES", parameters = { "dataStore" })
    @Documentation("The table name")
    private String tableName;

    @Option
    @Structure(type = Structure.Type.OUT/* , discoverSchema = "JDBCQueryDataSet" */)
    @Documentation("schema")
    private List<SchemaInfo> schema;

    // TODO how to generate Query Store field, Guess Query button here and hook it to the query field for runtime to
    // generate the query
    // TODO how to pass right connection info to SQL Editor field by button in Query field?
    // TODO how to process different action between studio and cloud
    // TODO how to process different action betwen tjdbcinput and tjdbcrow
    @Option
    @Code("sql")
    @Documentation("sql query")
    private String sqlQuery = "select id, name from employee";

    @Override
    public String getSqlQuery(Platform platform) {
        return sqlQuery;
    }

    @Override
    public boolean isTableMode() {
        return false;
    }
}
