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
package org.talend.components.jdbc.bulk;

import lombok.Data;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Structure;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.List;

@Data
@GridLayout({
        @GridLayout.Row("schema"),
        @GridLayout.Row("bulkCommonConfig"),
        @GridLayout.Row("append")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("bulkCommonConfig")
})
@Documentation("jdbc output bulk file")
public class JDBCOutputBulkConfig implements Serializable {

    @Option
    @Structure(type = Structure.Type.OUT)
    @Documentation("schema")
    private List<SchemaInfo> schema;

    @Option
    @Documentation("")
    private JDBCBulkCommonConfig bulkCommonConfig;

    // advanced setting

    @Option
    @Documentation("")
    private boolean append;

}
