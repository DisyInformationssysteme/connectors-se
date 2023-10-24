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
import org.talend.components.jdbc.output.JDBCSQLBuilder;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Structure;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Data
@GridLayout({
        @GridLayout.Row("schema"),
        @GridLayout.Row("dataStore"),
        @GridLayout.Row("tableName")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("dataStore")
})
@DataSet("JDBCTableDataSet")
@Documentation("A table dataset")
public class JDBCTableDataSet implements BaseDataSet, Serializable {

    @Option
    @Documentation("The connection information to execute")
    private JDBCDataStore dataStore;

    @Option
    @Suggestable(value = "FETCH_TABLES", parameters = { "dataStore" })
    @Documentation("The table name")
    private String tableName;

    // use new DiscoverSchemaExtended
    @Option
    @Structure(type = Structure.Type.OUT/* , discoverSchema = "JDBCTableDataSet" */)
    @Documentation("schema")
    private List<SchemaInfo> schema;

    @Override
    public String getSqlQuery(Platform platform) {
        // JDBCTableDataSet's getSqlQuery method should be only used for cloud source, so platform is impossible is null
        return JDBCSQLBuilder.getInstance().generateSQL4SelectTable(platform, this.getTableName(), this.getSchema());
    }

    @Override
    public boolean isTableMode() {
        return true;
    }
}
