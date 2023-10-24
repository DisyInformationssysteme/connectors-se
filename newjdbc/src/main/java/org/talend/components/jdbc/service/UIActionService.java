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
package org.talend.components.jdbc.service;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.components.jdbc.platforms.PlatformService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.DynamicValues;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.completion.Suggestions;
import org.talend.sdk.component.api.service.completion.Values;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.util.List;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

@Slf4j
@Service
public class UIActionService {

    @Service
    private JDBCService jdbcService;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private PlatformService platformService;

    @DynamicValues("ACTION_LIST_SUPPORTED_DB")
    public Values loadSupportedDataBaseTypes() {
        return new Values(platformService.getJdbcConfiguration()
                .get()
                .getDrivers()
                .stream()
                .filter(d -> platformService.driverNotDisabled(d))
                .sorted(comparingInt(JDBCConfiguration.Driver::getOrder))
                .map(driver -> new Values.Item(driver.getId(), driver.getDisplayName()))
                .collect(toList()));
    }

    @Suggestions("ACTION_LIST_HANDLERS_DB")
    public SuggestionValues getHandlersDataBaseTypes(@Option final String dbType) {
        List<JDBCConfiguration.Driver> drivers = platformService.getJdbcConfiguration()
                .get()
                .getDrivers()
                .stream()
                .filter(d -> platformService.driverNotDisabled(d))
                .collect(toList());
        return new SuggestionValues(false, drivers.stream()
                .filter(d -> platformService.driverNotDisabled(d))
                .filter(db -> db.getId().equals(dbType) && !db.getHandlers().isEmpty())
                .flatMap(db -> db.getHandlers().stream())
                .flatMap(handler -> drivers.stream().filter(d -> d.getId().equals(handler)))
                .distinct()
                .sorted(comparingInt(JDBCConfiguration.Driver::getOrder))
                .map(driver -> new SuggestionValues.Item(driver.getId(), driver.getDisplayName()))
                .collect(toList()));
    }

    @Suggestions("ACTION_SUGGESTION_TABLE_COLUMNS_NAMES")
    public SuggestionValues getTableColumns(@Option final JDBCTableDataSet dataSet) {
        List<String> listColumns = dataSet.getSchema()
                .stream()
                .map(column -> column.getOriginalDbColumnName() != null ? column.getOriginalDbColumnName()
                        : column.getLabel())
                .collect(toList());
        return new SuggestionValues(true,
                listColumns
                        .stream()
                        .map(columnName -> new SuggestionValues.Item(columnName, columnName))
                        .collect(toList()));
    }

}
