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
package org.talend.components.common.stream.format.json;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.talend.components.common.stream.format.ContentFormat;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@GridLayout({ @GridLayout.Row("jsonPointer") })
@GridLayout(names = GridLayout.FormType.ADVANCED,
        value = { @GridLayout.Row("pathTypeList"), @GridLayout.Row("forceDouble") })
@Documentation("Json Configuration with json pointer rules.")
public class JsonConfiguration implements ContentFormat {

    @Option
    @Documentation("Json pointer expression.")
    private String jsonPointer;

    @Option
    @DefaultValue("true")
    @Documentation("Force json number to double.")
    private boolean forceDouble = true;

    @Option
    @Documentation("Force type for given attributes.")
    private List<PathType> pathTypeList = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @GridLayout(names = GridLayout.FormType.ADVANCED, value = { @GridLayout.Row({ "path", "type" }) })
    @Documentation("Parameters configuration.")
    public static class PathType implements Serializable {

        @Option
        @Required
        @Documentation("Path to force the type.")
        private String path;

        @Option
        @Required
        @Documentation("The forced type for the given path.")
        private ForcedType type;

    }

    public enum ForcedType {
        INT,
        FLOAT,
        STRING,
        BOOLEAN,
        RECORD,
        ARRAY
    }

}
