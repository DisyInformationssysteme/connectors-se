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
package org.talend.components.jdbc.row;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.context.RuntimeContext;
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
import java.util.Map;

@Slf4j
@Version(1)
@Icon(value = Icon.IconType.CUSTOM, custom = "datastore-connector")
@Processor(name = "Row")
@Documentation("JDBC Row component.")
public class JDBCRowProcessor implements Serializable {

    private static final long serialVersionUID = 1;

    private final JDBCRowConfig configuration;

    private final JDBCService service;

    private final RecordBuilderFactory recordBuilderFactory;

    @Connection
    private transient JDBCService.DataSourceWrapper dataSource;

    @RuntimeContext
    private transient Map<String, Object> context;

    private transient JDBCRowWriter writer;

    private transient boolean init;

    public JDBCRowProcessor(@Option("configuration") final JDBCRowConfig configuration,
            final JDBCService service, final RecordBuilderFactory recordBuilderFactory) {
        this.configuration = configuration;
        this.service = service;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    @PostConstruct
    public void init() {
        // TODO now can't fetch design schema, only can get input record's schema
    }

    @ElementListener
    public void elementListener(@Input final Record record, @Output final OutputEmitter<Record> success,
            @Output("reject") final OutputEmitter<Record>/* OutputEmitter<Reject> */ reject) throws SQLException {
        if (!init) {
            boolean useExistedConnection = false;

            if (dataSource == null) {
                try {
                    dataSource = service.createConnectionOrGetFromSharedConnectionPoolOrDataSource(
                            configuration.getDataSet().getDataStore(), context, false);

                    if(configuration.getCommitEvery()!=0) {
                        java.sql.Connection connection = dataSource.getConnection();
                        if(connection.getAutoCommit()) {
                            connection.setAutoCommit(false);
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                useExistedConnection = true;
            }

            writer = new JDBCRowWriter(configuration, useExistedConnection, dataSource,
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

        // TODO correct this
        List<Record> rejectedWrites = writer.getRejectedWrites();
        for (Record r : rejectedWrites) {
            /*
             * Reject rt = new Reject();
             * rt.setRecord(r);
             * // TODO, this is right?
             * rt.setErrorCode("");
             * rt.setErrorMessage("");
             */
            reject.emit(r);
        }
    }

    @PreDestroy
    public void release() throws SQLException {
        if (writer != null) {
            writer.close();
        }
    }

}