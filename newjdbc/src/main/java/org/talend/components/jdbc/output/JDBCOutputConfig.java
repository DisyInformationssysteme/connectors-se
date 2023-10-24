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
package org.talend.components.jdbc.output;

import lombok.Data;
import org.talend.components.jdbc.common.DistributionStrategy;
import org.talend.components.jdbc.common.OperationKey;
import org.talend.components.jdbc.common.RedshiftSortStrategy;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.action.Validable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.condition.ActiveIfs;
import org.talend.sdk.component.api.configuration.condition.UIScope;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.talend.sdk.component.api.configuration.condition.ActiveIf.EvaluationStrategy.CONTAINS;
import static org.talend.sdk.component.api.configuration.condition.ActiveIfs.Operator.AND;
import static org.talend.sdk.component.api.configuration.condition.ActiveIfs.Operator.OR;

@Data
@GridLayout({
        @GridLayout.Row("dataSet"),
        @GridLayout.Row("dataAction"),
        @GridLayout.Row("clearData"),
        @GridLayout.Row("dieOnError"),

        // cloud special
        @GridLayout.Row("createTableIfNotExists"),
        @GridLayout.Row("varcharLength"),
        @GridLayout.Row("keysForCreateTableAndDML"),
        @GridLayout.Row("keysForDML"),
        @GridLayout.Row("sortStrategy"),
        @GridLayout.Row("sortKeys"),
        @GridLayout.Row("distributionStrategy"),
        @GridLayout.Row("distributionKeys"),
        @GridLayout.Row("ignoreUpdate")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("dataSet"),
        @GridLayout.Row("commitEvery"),
        @GridLayout.Row("additionalColumns"),
        @GridLayout.Row("useFieldOptions"),
        @GridLayout.Row("fieldOptions"),
        @GridLayout.Row("debugQuery"),
        @GridLayout.Row("useBatch"),
        @GridLayout.Row("batchSize"),
        @GridLayout.Row("useQueryTimeout"),
        @GridLayout.Row("queryTimeout"),

        // cloud special
        @GridLayout.Row("rewriteBatchedStatements"),
        @GridLayout.Row("useOriginColumnName")

})
@Documentation("jdbc output")
public class JDBCOutputConfig implements Serializable {

    @Option
    @Documentation("table dataset")
    private JDBCTableDataSet dataSet;

    // cloud special ui start================

    @Option
    @Required
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.CLOUD_SCOPE }),
            @ActiveIf(target = "dataAction", value = { "INSERT", "INSERT_OR_UPDATE", "UPDATE_OR_INSERT", "BULK_LOAD" }),
    })
    @Documentation("Create table if don't exists")
    private boolean createTableIfNotExists = false;

    @Option
    @Required
    @ActiveIf(target = "../createTableIfNotExists", value = { "true" })
    @Documentation("The length of varchar types. This value will be used to create varchar columns in this table."
            + "\n-1 means that the max supported length of the targeted database will be used.")
    private int varcharLength = -1;

    @Option
    @ActiveIf(target = "../createTableIfNotExists", value = { "true" })
    @Documentation("List of columns to be used as keys for create table and DML operation")
    private OperationKey keysForCreateTableAndDML = new OperationKey();

    @Option
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.CLOUD_SCOPE }),
            @ActiveIf(target = "../createTableIfNotExists", value = { "false" }),
            @ActiveIf(target = "../dataAction", value = { "INSERT", "BULK_LOAD" }, negate = true)
    })
    @Documentation("List of columns to be used as keys for DML operation")
    private OperationKey keysForDML = new OperationKey();

    @Option
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = "../dataSet.dataStore.dbType", value = { "Redshift" }),
            @ActiveIf(target = "../createTableIfNotExists", value = { "true" })
    })
    @Documentation("Define the sort strategy of Redshift table")
    private RedshiftSortStrategy sortStrategy = RedshiftSortStrategy.COMPOUND;

    @Option
    @Validable(value = "ACTION_VALIDATE_SORT_KEYS", parameters = { "../sortStrategy", "../sortKeys" })
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = "../dataSet.dataStore.dbType", value = { "Redshift" }),
            @ActiveIf(target = "../createTableIfNotExists", value = { "true" }),
            @ActiveIf(target = "../sortStrategy", value = { "NONE" }, negate = true)
    })
    @Suggestable(value = "ACTION_SUGGESTION_TABLE_COLUMNS_NAMES", parameters = { "../dataSet" })
    @Documentation("List of columns to be used as sort keys for redshift")
    private List<String> sortKeys = new ArrayList<>();

    @Option
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = "../dataSet.dataStore.dbType", value = { "Redshift" }),
            @ActiveIf(target = "../createTableIfNotExists", value = { "true" })
    })
    @Documentation("Define the distribution strategy of Redshift table")
    private DistributionStrategy distributionStrategy = DistributionStrategy.AUTO;

    @Option
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = "../dataSet.dataStore.dbType", value = { "Redshift" }),
            @ActiveIf(target = "../distributionStrategy", value = { "KEYS" }),
            @ActiveIf(target = "../createTableIfNotExists", value = { "true" })
    })
    @Suggestable(value = "ACTION_SUGGESTION_TABLE_COLUMNS_NAMES", parameters = { "../dataSet" })
    @Documentation("List of columns to be used as distribution keys for redshift")
    private List<String> distributionKeys = new ArrayList<>();

    @Option
    @Suggestable(value = "ACTION_SUGGESTION_TABLE_COLUMNS_NAMES", parameters = { "../dataSet" })
    @ActiveIfs(operator = AND, value = {
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.CLOUD_SCOPE }),
            @ActiveIf(target = "../dataAction", value = { "UPDATE", "UPSERT", "INSERT_OR_UPDATE", "UPDATE_OR_INSERT" })
    })
    @Documentation("List of columns to be ignored from update")
    private List<String> ignoreUpdate = new ArrayList<>();

    // can't move to datastore as migration, for example, two output config with different rewriteBatchedStatements,
    // but use the same datastore
    @Option
    @ActiveIfs(operator = OR, value = {
            @ActiveIf(target = "../dataSet.dataStore.dbType", value = { "MySQL" }),
            @ActiveIf(target = "../dataSet.dataStore.handler", evaluationStrategy = CONTAINS, value = { "MySQL" })
    })
    @Documentation("Rewrite batched statements, to execute one statement per batch combining values in the sql query")
    private boolean rewriteBatchedStatements = true;

    @Option
    // should not introduce this to studio, as it's a cloud history issue
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.CLOUD_SCOPE })
    @Documentation("To keep the old behavior that use sanitized name as column name")
    private boolean useOriginColumnName = true;

    // cloud special ui end================

    @Option
    @Documentation("")
    private DataAction dataAction = DataAction.INSERT;

    @Option
    // this is not easy to control the multitasks run cross thread/vm/machine, so disable it for cloud
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean clearData;

    // this decide throw exception or ignore exception for some case for current task,
    // if multitasks, depend on runtime platform how to process if one task fail
    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean dieOnError;

    // disable it for bulk api for snowflake/(redshift bulk)/(sqldwh bulk) as this is conflict with bulk api
    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private int commitEvery = 10000;

    // disable it for bulk api for snowflake/(redshift bulk)/(sqldwh bulk) as this is conflict with bulk api
    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private List<AdditionalColumn> additionalColumns = Collections.emptyList();

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean useFieldOptions;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @ActiveIf(target = "useFieldOptions", value = { "true" })
    @Documentation("")
    private List<FieldOption> fieldOptions = Collections.emptyList();

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean debugQuery;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean useBatch = true;

    // use this instead of platform/tck runtime fixed one from @BeforeGroup and @AfterGroup
    // cloud always use batch way
    @Option
    @ActiveIf(target = "useBatch", value = { "true" })
    @Documentation("")
    private int batchSize = 10000;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("")
    private boolean useQueryTimeout;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @ActiveIf(target = "useQueryTimeout", value = { "true" })
    @Documentation("")
    private int queryTimeout = 30;

    public List<String> getKeys() {
        if (createTableIfNotExists) {
            return keysForCreateTableAndDML.getKeys();
        }
        return keysForDML.getKeys();
    }

}
