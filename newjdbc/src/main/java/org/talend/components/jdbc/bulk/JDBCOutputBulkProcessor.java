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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;
import org.talend.sdk.component.api.record.Record;

import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.sql.SQLException;

@Slf4j
@Getter
@Version(1)
// @Processor(name = "OutputBulk") //TODO fix this : The component
// org.talend.components.jdbc.bulk.JDBCOutputBulkProcessor is missing a dataset in its configuration (see @DataSet)
@Icon(value = Icon.IconType.CUSTOM, custom = "datastore-connector")
@Documentation("JDBC Output Bulk component")
public class JDBCOutputBulkProcessor implements Serializable {

    private final JDBCOutputBulkConfig configuration;

    private final JDBCService jdbcService;

    // private final I18nMessage i18n;

    // private transient boolean init;

    public JDBCOutputBulkProcessor(final JDBCOutputBulkConfig outputConfig, final JDBCService jdbcService/*
                                                                                                          * , final
                                                                                                          * I18nMessage
                                                                                                          * i18nMessage
                                                                                                          */) {
        this.configuration = outputConfig;
        this.jdbcService = jdbcService;
        // this.i18n = i18nMessage;
    }

    @BeforeGroup
    public void beforeGroup() {

    }

    @ElementListener
    public void elementListener(@Input final Record record) throws SQLException {

    }

    @AfterGroup
    public void afterGroup() throws SQLException {
    }

    @PreDestroy
    public void preDestroy() {
    }

}
