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
package org.talend.components.jdbc.sp;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.context.RuntimeContext;
import org.talend.sdk.component.api.context.RuntimeContextHolder;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.connection.Connection;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Version(1)
@Icon(value = Icon.IconType.CUSTOM, custom = "JDBCSP")
@Processor(name = "SP")
@Documentation("JDBC SP component.")
public class JDBCSPProcessor implements Serializable {

    private static final long serialVersionUID = 1;

    private final JDBCSPConfig configuration;

    private final JDBCService service;

    private transient JDBCSPWriter writer;

    private transient boolean init;

    @Connection
    private transient java.sql.Connection connection;

    private transient JDBCService.DataSourceWrapper dataSource;

    @RuntimeContext
    private transient RuntimeContextHolder context;

    private final RecordBuilderFactory recordBuilderFactory;

    public JDBCSPProcessor(@Option("configuration") final JDBCSPConfig configuration,
            final JDBCService service, final RecordBuilderFactory recordBuilderFactory) {
        this.configuration = configuration;
        this.service = service;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    @PostConstruct
    public void init() {
    }

    @ElementListener
    public void elementListener(@Input final Record record, @Output final OutputEmitter<Record> success)
            throws SQLException {
        if (!init) {
            boolean useExistedConnection = false;

            if (connection == null) {
                dataSource = service.createConnectionOrGetFromSharedConnectionPoolOrDataSource(
                        configuration.getDataStore(), context, false);
            } else {
                useExistedConnection = true;
                dataSource = new JDBCService.DataSourceWrapper(null, connection);
            }

            writer = new JDBCSPWriter(configuration, useExistedConnection, dataSource,
                    recordBuilderFactory);

            writer.open();

            init = true;
        }

        // as output component which have input line, it's impossible that record is null
        // as standalone or input component, it's possible that record is null
        writer.write(record);

        List<Record> successfulWrites = writer.getSuccessfulWrites();
        for (Record r : successfulWrites) {
            success.emit(r);
        }
    }

    @PreDestroy
    public void release() throws SQLException {
        if (writer != null) {
            writer.close();
        }
    }

}