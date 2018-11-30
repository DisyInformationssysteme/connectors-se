/*
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
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
package org.talend.components.azure.common;

import java.io.Serializable;

import lombok.Data;

import org.talend.components.azure.service.AzureComponentServices;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.meta.Documentation;

@Data
@DataSet("AzureDataSet")
public class AzureTableConnection implements Serializable {

    @Option
    @Documentation("Azure Connection")
    private AzureConnection connection;

    @Option
    @Documentation("The name of the table to access")
    @Suggestable(value = AzureComponentServices.GET_TABLE_NAMES, parameters = "connection")
    private String tableName;
}
