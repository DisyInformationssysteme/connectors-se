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
import org.talend.components.jdbc.common.PreparedStatementParameter;
import org.talend.components.jdbc.common.Type;
import org.talend.components.jdbc.dataset.JDBCQueryDataSet;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.row.JDBCRowConfig;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.output.Branches;

import javax.json.bind.Jsonb;
import javax.json.bind.spi.JsonbProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.talend.components.jdbc.DBTestUtils.getValueByIndex;

@WithComponents("org.talend.components.jdbc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Testing of JDBC row component")
public class JDBCRowTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private JDBCService jdbcService;

    private JDBCDataStore dataStore;

    private static final String tableName = "JDBCROW";

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
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(4, 'momo')");
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        DBTestUtils.runProcessorWithoutInput(componentsHandler, config, null);

        List<Record> result =
                DBTestUtils.runInput(componentsHandler, dataStore, tableName, DBTestUtils.createTestSchemaInfos());

        assertEquals(4, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("momo", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void test_use_preparedstatement_no_connector() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(?, ?)");
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);
        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 4),
                new PreparedStatementParameter(2, Type.String, "momo")));

        DBTestUtils.runProcessorWithoutInput(componentsHandler, config, Arrays.asList(4, "momo"));

        List<Record> result =
                DBTestUtils.runInput(componentsHandler, dataStore, tableName, DBTestUtils.createTestSchemaInfos());

        assertEquals(4, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("momo", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void test_die_on_error_no_connector() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(4, 'a too long value')");
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        try {
            DBTestUtils.runProcessorWithoutInput(componentsHandler, config, null);
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void test_basic_as_input() throws SQLException {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("select id, name from " + tableName);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos4RowResultSet());
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        config.setPropagateRecordSet(true);// the field is the unique reason to use the component as a input
                                           // component
        config.setRecordSetColumn("RESULTSET");

        // this is not valid usage for cloud platform as jdbcrow is processor component in fact, only valid for studio
        // platform,
        // so can't call common api to run it here
        Map<String, List<?>> outputs = DBTestUtils.runProcessorWithoutInput(componentsHandler, config, null);
        List<Record> result = List.class.cast(outputs.get(Branches.DEFAULT_BRANCH));
        Object jdbcResultSetObject = result.get(0).get(Object.class, "RESULTSET");
        assertTrue(jdbcResultSetObject != null && ResultSet.class.isInstance(jdbcResultSetObject));
    }

    @Test
    public void test_use_preparedstatement_as_input() throws SQLException {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("select id, name from " + tableName + " where id = ?");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos4RowResultSet());
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        config.setPropagateRecordSet(true);// the field is the unique reason to use the component as a input
        // component
        config.setRecordSetColumn("RESULTSET");

        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 1)));

        // this is not valid usage for cloud platform as jdbcrow is processor component in fact, only valid for studio
        // platform,
        // so can't call common api to run it here
        Map<String, List<?>> outputs =
                DBTestUtils.runProcessorWithoutInput(componentsHandler, config, Arrays.asList(1));
        List<Record> result = List.class.cast(outputs.get(Branches.DEFAULT_BRANCH));
        Object jdbcResultSetObject = result.get(0).get(Object.class, "RESULTSET");
        assertTrue(jdbcResultSetObject != null && ResultSet.class.isInstance(jdbcResultSetObject));

        // System.out.println("===>" + ResultSet.class.cast(jdbcResultSetObject).next());
        // System.out.println("===>" + ResultSet.class.cast(jdbcResultSetObject).getString(1));

        // final Jsonb jsonb = JsonbProvider.provider().create().build();
        // String ser = jsonb.toJson(jdbcResultSetObject);
        // ResultSet resultSet = ResultSet.class.cast(jsonb.fromJson(ser, jdbcResultSetObject.getClass()));
    }

    @Test
    public void test_reject_as_input() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("select id, name from notexists");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos4RowResultSet());
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        config.setPropagateRecordSet(true);// the field is the unique reason to use the component as a input
        // component
        config.setRecordSetColumn("RESULTSET");

        try {
            DBTestUtils.runProcessorWithoutInput(componentsHandler, config, null);
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void test_basic_as_output() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(?,?)");
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 4),
                new PreparedStatementParameter(2, Type.String, "momo")));

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "xiaobai").build());

        BaseComponentsHandler.Outputs outputs =
                DBTestUtils.runProcessor(records, componentsHandler, config, Arrays.asList(4, "momo"));
        assertEquals(2, outputs.get(Record.class, Branches.DEFAULT_BRANCH).size());
        assertNull(outputs.get(Record.class, "reject"));

        List<Record> result =
                DBTestUtils.runInput(componentsHandler, dataStore, tableName, DBTestUtils.createTestSchemaInfos());

        assertEquals(5, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("momo", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(4), 0));
        assertEquals("momo", getValueByIndex(result.get(4), 1));
    }

    @Test
    public void test_basic_not_use_prepared_statement_as_output() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(4,'momo')");
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "xiaobai").build());

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config, null);
        assertEquals(2, outputs.get(Record.class, Branches.DEFAULT_BRANCH).size());
        assertNull(outputs.get(Record.class, "reject"));

        List<Record> result =
                DBTestUtils.runInput(componentsHandler, dataStore, tableName, DBTestUtils.createTestSchemaInfos());

        assertEquals(5, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("momo", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(4), 0));
        assertEquals("momo", getValueByIndex(result.get(4), 1));
    }

    @Test
    public void test_reject_as_output() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(?,?)");
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        config.setDataSet(dataSet);
        config.setDieOnError(false);
        randomCommit(config);

        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 4),
                new PreparedStatementParameter(2, Type.String, "a too long value")));

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        BaseComponentsHandler.Outputs outputs =
                DBTestUtils.runProcessor(records, componentsHandler, config, Arrays.asList(4, "a too long value"));
        assertNull(outputs.get(Record.class, Branches.DEFAULT_BRANCH));
        assertEquals(2, outputs.get(Record.class, "reject").size());

        List<Record> result =
                DBTestUtils.runInput(componentsHandler, dataStore, tableName, DBTestUtils.createTestSchemaInfos());

        assertEquals(3, result.size());
    }

    @Test
    public void test_die_on_error_as_output() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("insert into " + tableName + " values(?,?)");
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        config.setDataSet(dataSet);
        config.setDieOnError(true);
        randomCommit(config);

        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 4),
                new PreparedStatementParameter(2, Type.String, "a too long value")));

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        try {
            DBTestUtils.runProcessor(records, componentsHandler, config, Arrays.asList(4, "a too long value"));
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void test_propagate_query_result_set_as_output() {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setTableName(tableName);
        dataSet.setSqlQuery("select id, name from " + tableName + " where id = ?");
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfosWithResultSet());
        config.setDataSet(dataSet);
        config.setDieOnError(false);
        randomCommit(config);

        config.setUsePreparedStatement(true);
        config.setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 3)));

        config.setPropagateRecordSet(true);
        config.setRecordSetColumn("RESULTSET");

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        BaseComponentsHandler.Outputs outputs =
                DBTestUtils.runProcessor(records, componentsHandler, config, Arrays.asList(3));
        List<Record> result = outputs.get(Record.class, Branches.DEFAULT_BRANCH);
        assertEquals(2, result.size());
        assertNull(outputs.get(Record.class, "reject"));
        Object jdbcResultSetObject = result.get(0).get(Object.class, "RESULTSET");
        assertTrue(jdbcResultSetObject != null && ResultSet.class.isInstance(jdbcResultSetObject));
    }

    private String randomCommit(JDBCRowConfig config) {
        config.setCommitEvery(DBTestUtils.randomInt());
        return new StringBuilder().append("commitEvery:").append(config.getCommitEvery()).toString();
    }

}
