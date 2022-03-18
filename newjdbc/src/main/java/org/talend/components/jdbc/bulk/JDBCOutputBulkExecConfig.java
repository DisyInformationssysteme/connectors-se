/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
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
package org.talend.components.jdbc.bulk;

import lombok.Data;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@Data
@GridLayout({
        @GridLayout.Row("dataSet"),
        @GridLayout.Row("bulkCommonConfig"),
        @GridLayout.Row("append")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("dataSet"),
        @GridLayout.Row("bulkCommonConfig")
})
@Documentation("jdbc bulk exec")
public class JDBCOutputBulkExecConfig implements Serializable {

    @Option
    @Documentation("table dataset")
    private JDBCTableDataSet dataSet;

    // TODO studio will add schema field auto
    // TODO but how to add guess schema auto for tjdbcrow, which should guess from table

    @Option
    @Documentation("")
    private JDBCBulkCommonConfig bulkCommonConfig;

    @Option
    @Documentation("")
    private boolean append;
}
