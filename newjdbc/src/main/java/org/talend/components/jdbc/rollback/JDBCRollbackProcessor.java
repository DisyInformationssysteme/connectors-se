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
package org.talend.components.jdbc.rollback;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.commit.JDBCCommitConfig;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.connection.Connection;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.io.Serializable;
import java.sql.SQLException;

@Slf4j
@Getter
@Version(1)
@Processor(name = "Rollback")
@Icon(value = Icon.IconType.CUSTOM, custom = "datastore-connector")
@Documentation("JDBC rollback component")
public class JDBCRollbackProcessor implements Serializable {

    private static final long serialVersionUID = 1;

    private final JDBCCommitConfig configuration;

    private final JDBCService jdbcService;

    @Connection
    private transient JDBCService.DataSourceWrapper connection;

    // private final I18nMessage i18n;

    // private transient boolean init;

    private final RecordBuilderFactory recordBuilderFactory;

    public JDBCRollbackProcessor(@Option("configuration") final JDBCCommitConfig configuration,
            final JDBCService jdbcService, final RecordBuilderFactory recordBuilderFactory) {
        this.configuration = configuration;
        this.jdbcService = jdbcService;
        // this.i18n = i18nMessage;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    @ElementListener
    public void elementListener(@Input final Record record, @Output final OutputEmitter<Record> success)
            throws SQLException {
        if (connection == null) {
            throw new RuntimeException("can't find the connection object");
        }

        if (!connection.getConnection().isClosed()) {
            connection.getConnection().rollback();

            if (configuration.isClose()) {
                connection.close();
            }
        }

        success.emit(record);
    }

}