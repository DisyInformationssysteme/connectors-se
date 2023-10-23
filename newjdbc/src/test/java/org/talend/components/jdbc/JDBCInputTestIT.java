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

import static org.junit.jupiter.api.Assertions.*;

import org.talend.components.jdbc.common.PreparedStatementParameter;
import org.talend.components.jdbc.common.Type;
import org.talend.components.jdbc.dataset.JDBCQueryDataSet;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.input.*;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.exception.ComponentException;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.talend.components.jdbc.DBTestUtils.*;

@WithComponents("org.talend.components.jdbc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Testing of JDBC input component")
public class JDBCInputTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private JDBCService jdbcService;

    private JDBCDataStore dataStore;

    private static final String tableName = "JDBCINPUT";

    private static final String tableName_all_type = "JDBCINPUTALLTYPE";

    @BeforeAll
    public void beforeAll() throws Exception {
        dataStore = DBTestUtils.createDataStore(true);
        JDBCService service = componentsHandler.findService(JDBCService.class);
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.createTestTable(conn, tableName);
            DBTestUtils.createAllTypesTable(conn, tableName_all_type);
        }
    }

    @AfterAll
    public void afterAll() throws SQLException {
        JDBCService service = componentsHandler.findService(JDBCService.class);
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.dropTestTable(conn, tableName);
            DBTestUtils.dropAllTypesTable(conn, tableName_all_type);
        } finally {
            DBTestUtils.shutdownDBIfNecessary();
        }
    }

    @BeforeEach
    public void before() throws SQLException {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            DBTestUtils.truncateTable(conn, tableName);
            DBTestUtils.loadTestData(conn, tableName);
            DBTestUtils.truncateAllTypesTable(conn, tableName_all_type);
            DBTestUtils.loadAllTypesData(conn, tableName_all_type);
        }
    }

    @Test
    public void testGetSchemaNames() throws Exception {
        SuggestionValues values = jdbcService.fetchTables(dataStore);

        assertTrue(values != null && values.getItems() != null);
        assertTrue(!values.getItems().isEmpty());

        boolean exists = false;
        for (SuggestionValues.Item name : values.getItems()) {
            if (tableName.equals(name.getId().toUpperCase())) {
                exists = true;
                break;
            }
        }

        assertTrue(exists);
    }

    public void testGetSchemaNamesWithWrongClass() throws Exception {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(true);
        dataStore.setJdbcClass("notexist");
        try {
            jdbcService.fetchTables(dataStore);
        } catch (ComponentException e) {
            assertTrue(e.getMessage().contains("notexist"));
        }
        fail();
    }

    public void testGetSchemaWithWrongClass() throws Exception {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(true);
        dataStore.setJdbcClass("notexist");
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setTableName(tableName);
        try {
            jdbcService.guessSchemaByTable(dataSet);
        } catch (ComponentException e) {
            assertTrue(e.getMessage().contains("notexist"));
        }
        fail();
    }

    public void testGetSchemaFromQueryWithWrongQuery() throws Exception {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL("notexist"));
        try {
            jdbcService.guessSchemaByQuery(dataSet);
        } catch (ComponentException e) {
            assertTrue(e.getMessage().contains("notexist"));
        }
        fail();
    }

    public void testGetSchemaFromQueryWithWrongClass() throws Exception {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(true);
        dataStore.setJdbcClass("notexist");
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));
        try {
            jdbcService.guessSchemaByQuery(dataSet);
        } catch (ComponentException e) {
            assertTrue(e.getMessage().contains("notexist"));
        }
        fail();
    }

    public void testGetSchemaFromQueryWithWrongURL() throws Exception {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(true);
        dataStore.setJdbcUrl("wrongone");
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));
        try {
            jdbcService.guessSchemaByQuery(dataSet);
        } catch (ComponentException e) {
            assertTrue(e.getMessage().contains("wrongone"));
        }
        fail();
    }

    @Test
    public void testGetSchema() throws Exception {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        // TODO support mapping files
        // the getResource method will convert "@" to "%40" when work with maven together, not sure the bug appear
        // where, bug make
        // sure it come from the env, not the function code, so only convert here
        // in the product env, the mappings_url is passed from the platform
        // java.net.URL mappings_url = this.getClass().getResource("/mappings");
        // mappings_url = DBTestUtils.correctURL(mappings_url);

        Schema schema = jdbcService.guessSchemaByQuery(dataSet);

        List<Schema.Entry> columns = schema.getEntries();
        DBTestUtils.testMetadata(columns);
    }

    @Disabled
    @Test
    public void testReaderWithCloudStyleAndQueryDataSet() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setSqlQuery(DBTestUtils.getSQL("test"));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);
        System.out.println(data);
    }

    @Disabled
    @Test
    public void testReaderWithCloudStyleAndQueryDataSetWithDesignSchema() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setSqlQuery(DBTestUtils.getSQL("test"));
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);
        System.out.println(data);
    }

    @Disabled
    @Test
    public void testReaderWithCloudStyleAndTableDataSet() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");

        JDBCTableInputConfig config = new JDBCTableInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);
        System.out.println(data);
    }

    @Disabled
    @Test
    public void testReaderWithCloudStyleAndSnowflakeAndTableDataSet() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleSnowflakeDataStore(true));
        dataSet.setTableName("test");

        JDBCTableInputConfig config = new JDBCTableInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);
        System.out.println(data);
    }

    @Disabled
    @Test
    public void testReaderWithCloudStyleAndTableDataSetWithDesignSchema() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCTableInputConfig config = new JDBCTableInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);
        System.out.println(data);
    }

    @Test
    public void testReader() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(3, data.size());

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals(" gaoyan ", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    @Test
    public void testReaderDynamic() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(3, data.size());

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals(" gaoyan ", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    @Test
    public void testTrimAll() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());
        config.getConfig().setTrimAllStringOrCharColumns(true);

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals("gaoyan", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    @Test
    public void testTrimField() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());
        config.getConfig().setColumnTrims(Arrays.asList(new ColumnTrim("ID", false), new ColumnTrim("NAME", true)));

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals("gaoyan", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    @Test
    public void testTrimFieldWithDynamicColumnOnly() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestDynamicSchemaInfos());
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());
        config.getConfig().setColumnTrims(Arrays.asList(new ColumnTrim("DYN", true)));

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals("gaoyan", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    @Test
    public void testTrimFieldWithDynamicColumnMix() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSchema(DBTestUtils.createTestDynamicMixSchemaInfos());
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());
        config.getConfig().setColumnTrims(Arrays.asList(new ColumnTrim("ID", false), new ColumnTrim("DYN", true)));

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(1, getValueByIndex(data.get(0), 0));
        assertEquals("wangwei", getValueByIndex(data.get(0), 1));

        assertEquals(2, getValueByIndex(data.get(1), 0));
        assertEquals("gaoyan", getValueByIndex(data.get(1), 1));

        assertEquals(3, getValueByIndex(data.get(2), 0));
        assertEquals("dabao", getValueByIndex(data.get(2), 1));
    }

    // TODO fix the mapping
    @Test
    public void testReaderAllTypesString() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName_all_type));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);

        List<Record> data = DBTestUtils.runInput(componentsHandler, config);

        Record record = data.get(0);

        // DiRecordVisitor & DiRowStructVisitor will process the convert action between tck type and studio type auto
        // tck no short type, so though we map to studio id_Short type, here have to get Integer
        Integer col0 = (Integer) getValueByIndex(record, 0);
        Integer col1 = (Integer) getValueByIndex(record, 1);
        Long col2 = (Long) getValueByIndex(record, 2);
        Float col3 = (Float) getValueByIndex(record, 3);
        Double col4 = (Double) getValueByIndex(record, 4);
        BigDecimal col5 = (BigDecimal) getValueByIndex(record, 5);
        String col6 = (String) getValueByIndex(record, 6);
        String col7 = (String) getValueByIndex(record, 7);
        // TODO support Object type in tck framework for studio, without ser/deser : TCOMP-2292
        // byte[] col8 = (byte[]) getValueByIndex(record, 8);
        // String col9 = (String) getValueByIndex(record, 9);
        Timestamp col10 = Timestamp.from(record.getInstant(record.getSchema().getEntries().get(10).getName()));
        Timestamp col11 = Timestamp.from(record.getInstant(record.getSchema().getEntries().get(11).getName()));
        Timestamp col12 = Timestamp.from(record.getInstant(record.getSchema().getEntries().get(12).getName()));
        Boolean col13 = (Boolean) getValueByIndex(record, 13);

        assertEquals(32767, col0.shortValue());
        assertEquals(2147483647, col1.intValue());
        assertEquals(9223372036854775807l, col2.longValue());
        assertTrue(col3 > 1);
        assertTrue(col4 > 2);
        assertEquals(new BigDecimal("1234567890.1234567890"), col5);
        assertEquals("abcd", col6);
        assertEquals("abcdefg", col7);
        byte[] blob = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        // assertArrayEquals(blob, col8);
        // assertEquals("abcdefg", col9);
        assertEquals("2016-12-28", new SimpleDateFormat("yyyy-MM-dd").format(col10));
        assertEquals("14:30:33", new SimpleDateFormat("HH:mm:ss").format(col11));
        // TODO fix precision : TCOMP-2293
        assertEquals(Timestamp.valueOf("2016-12-28 14:31:56.12345"), col12);
        assertEquals(true, col13);

        Schema actualSchema = record.getSchema();
        List<Schema.Entry> actualFields = actualSchema.getEntries();

        assertEquals(14, actualFields.size());
    }

    @Test
    @Disabled
    public void testUsePrepareStatement() throws SQLException {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName_all_type) + " where INT_COL = ? and VARCHAR_COL = ?");

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.getConfig().setUsePreparedStatement(true);
        config.getConfig()
                .setPreparedStatementParameters(Arrays.asList(new PreparedStatementParameter(1, Type.Int, 2147483647),
                        new PreparedStatementParameter(2, Type.String, "abcdefg")));

        // skip parameter ser, as PreparedStatementParameter use object to store value, and the function only for studio
        List<Record> records = new ArrayList<>();
        QueryEmitter qe = new QueryEmitter(config, jdbcService, recordBuilderFactory);
        try {
            qe.init();
            Record r;
            while ((r = qe.next()) != null) {
                records.add(r);
            }
        } finally {
            qe.release();
        }

        Record record = records.get(0);

        Integer col0 = (Integer) getValueByIndex(record, 0);
        Integer col1 = (Integer) getValueByIndex(record, 1);
        Long col2 = (Long) getValueByIndex(record, 2);
        Float col3 = (Float) getValueByIndex(record, 3);
        Double col4 = (Double) getValueByIndex(record, 4);
        BigDecimal col5 = (BigDecimal) getValueByIndex(record, 5);
        String col6 = (String) getValueByIndex(record, 6);
        String col7 = (String) getValueByIndex(record, 7);
        // byte[] col8 = (byte[]) getValueByIndex(record, 8);
        // String col9 = (String) getValueByIndex(record, 9);
        Timestamp col10 = new Timestamp(Long.class.cast(getValueByIndex(record, 10)));
        Timestamp col11 = new Timestamp(Long.class.cast(getValueByIndex(record, 11)));
        Timestamp col12 = new Timestamp(Long.class.cast(getValueByIndex(record, 12)));
        Boolean col13 = (Boolean) getValueByIndex(record, 13);

        assertEquals(32767, col0.shortValue());
        assertEquals(2147483647, col1.intValue());
        assertEquals(9223372036854775807l, col2.longValue());
        assertTrue(col3 > 1);
        assertTrue(col4 > 2);
        assertEquals(new BigDecimal("1234567890.1234567890"), col5);
        assertEquals("abcd", col6);
        assertEquals("abcdefg", col7);
        byte[] blob = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        // assertArrayEquals(blob, col8);
        // assertEquals("abcdefg", col9);
        assertEquals("2016-12-28", new SimpleDateFormat("yyyy-MM-dd").format(col10));
        assertEquals("14:30:33", new SimpleDateFormat("HH:mm:ss").format(col11));
        assertEquals(Timestamp.valueOf("2016-12-28 14:31:56.12345"), col12);
        assertEquals(true, col13);

        Schema actualSchema = record.getSchema();
        List<Schema.Entry> actualFields = actualSchema.getEntries();

        assertEquals(14, actualFields.size());
    }

    @Test
    public void testType() {
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        dataSet.setSqlQuery(DBTestUtils.getSQL(tableName));

        JDBCInputConfig config = new JDBCInputConfig();
        config.setDataSet(dataSet);
        config.setConfig(new JDBCCommonInputConfig());

        List<Record> records = DBTestUtils.runInput(componentsHandler, config);

        assertEquals(Schema.Type.INT, records.get(0).getSchema().getEntries().get(0).getType());
        assertEquals(Schema.Type.STRING, records.get(0).getSchema().getEntries().get(1).getType());
    }

}
