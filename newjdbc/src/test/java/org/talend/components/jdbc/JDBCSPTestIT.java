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
package org.talend.components.jdbc;

import org.junit.jupiter.api.*;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.components.jdbc.sp.JDBCSPConfig;
import org.talend.components.jdbc.sp.ParameterType;
import org.talend.components.jdbc.sp.SPParameter;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.output.Branches;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithComponents("org.talend.components.jdbc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Testing of JDBC sp component")
public class JDBCSPTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private JDBCService jdbcService;

    private JDBCDataStore dataStore;

    private static final String tableName = "JDBCSP";

    @BeforeAll
    public void beforeAll() throws Exception {
        dataStore = DBTestUtils.createDataStore(false);

        JDBCService service = componentsHandler.findService(JDBCService.class);
        try (JDBCService.DataSourceWrapper dataSourceWrapper =
                service.createDataSource(DBTestUtils.createDataStore(true));
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.createTestTable(conn, tableName);
        }
    }

    @AfterAll
    public void afterAll() throws Exception {
        JDBCService service = componentsHandler.findService(JDBCService.class);
        try (JDBCService.DataSourceWrapper dataSourceWrapper =
                service.createDataSource(DBTestUtils.createDataStore(true));
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.dropTestTable(conn, tableName);
        } finally {
            DBTestUtils.shutdownDBIfNecessary();
        }
    }

    @BeforeEach
    public void before() throws Exception {
        try (JDBCService.DataSourceWrapper dataSourceWrapper =
                jdbcService.createDataSource(DBTestUtils.createDataStore(true));
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.truncateTable(conn, tableName);
            DBTestUtils.loadTestData(conn, tableName);
        }
    }

    @Test
    public void test_basic_no_connector() {
        JDBCSPConfig config = new JDBCSPConfig();
        config.setDataStore(dataStore);
        config.setSpName("SYSCS_UTIL.SYSCS_EMPTY_STATEMENT_CACHE");

        DBTestUtils.runProcessorWithoutInput(componentsHandler, config);
    }

    @Test
    public void test_basic_as_input() {
        JDBCSPConfig config = new JDBCSPConfig();
        config.setDataStore(dataStore);
        config.setFunction(true);
        config.setResultColumn("PARAMETER");
        config.setSpName("SYSCS_UTIL.SYSCS_GET_DATABASE_NAME");

        config.setSchema(DBTestUtils.createSPSchemaInfo2());

        Map<String, List<?>> outputs = DBTestUtils.runProcessorWithoutInput(componentsHandler, config);

        List<Record> result = List.class.cast(outputs.get(Branches.DEFAULT_BRANCH));

        assertEquals("memory:myDB", result.get(0).getString("PARAMETER"));
    }

    @Test
    public void test_basic_as_output_and_no_input() {
        JDBCSPConfig config = new JDBCSPConfig();
        config.setDataStore(dataStore);

        config.setSpName("SYSCS_UTIL.SYSCS_DISABLE_LOG_ARCHIVE_MODE");
        config.setSchema(DBTestUtils.createSPSchemaInfo1());
        config.setSpParameters(Arrays.asList(new SPParameter("PARAMETER", ParameterType.IN)));

        Schema schema = DBTestUtils.createSPSchema1(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("PARAMETER", 0).build());

        DBTestUtils.runProcessor(records, componentsHandler, config);
    }

    @Test
    public void test_basic_as_output_and_input() {
        JDBCSPConfig config = new JDBCSPConfig();
        config.setDataStore(dataStore);

        config.setSpName("SYSCS_UTIL.SYSCS_DISABLE_LOG_ARCHIVE_MODE");
        config.setSchema(DBTestUtils.createSPSchemaInfo3());
        config.setSpParameters(Arrays.asList(new SPParameter("PARAMETER1", ParameterType.IN)));

        Schema schema = DBTestUtils.createSPSchema3(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("PARAMETER1", 0)
                .withString("PARAMETER2", "wangwei")
                .build());

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        List<Record> result = outputs.get(Record.class, Branches.DEFAULT_BRANCH);
        assertEquals(1, result.size());
        assertEquals(0, DBTestUtils.getValueByIndex(records.get(0), 0));
        assertEquals("wangwei", DBTestUtils.getValueByIndex(records.get(0), 1));
    }

}
