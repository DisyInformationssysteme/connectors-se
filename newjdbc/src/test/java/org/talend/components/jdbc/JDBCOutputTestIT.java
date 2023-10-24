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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.dataset.JDBCQueryDataSet;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.input.JDBCCommonInputConfig;
import org.talend.components.jdbc.input.JDBCInputConfig;
import org.talend.components.jdbc.input.JDBCTableInputConfig;
import org.talend.components.jdbc.output.DataAction;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.service.JDBCService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.talend.components.jdbc.DBTestUtils.*;

@Slf4j
@WithComponents("org.talend.components.jdbc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Testing of JDBC output component")
public class JDBCOutputTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private JDBCService jdbcService;

    private JDBCDataStore dataStore;

    private static final String tableName = "JDBCOUTPUT";

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
    public void testDynamicInsert() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        List<SchemaInfo> schemaInfos = DBTestUtils.createTestDynamicSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(5, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("xiaoming", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(5), getValueByIndex(result.get(4), 0));
        assertEquals("xiaobai", getValueByIndex(result.get(4), 1));
    }

    @Test
    public void testInsert() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        assertEquals(records, outputs.get(Record.class, Branches.DEFAULT_BRANCH));

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(5, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("xiaoming", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(5), getValueByIndex(result.get(4), 0));
        assertEquals("xiaobai", getValueByIndex(result.get(4), 1));
    }

    @Test
    public void testBatch() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 6).withString("NAME", "xiaohong").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 7).withString("NAME", "xiaored").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 8).withString("NAME", "xiaohei").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT);
        config.setDieOnError(true);
        config.setUseBatch(true);
        config.setBatchSize(2);
        config.setCommitEvery(3);

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        assertEquals(records, outputs.get(Record.class, Branches.DEFAULT_BRANCH));

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(8, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("xiaoming", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(5), getValueByIndex(result.get(4), 0));
        assertEquals("xiaobai", getValueByIndex(result.get(4), 1));
        assertEquals(new Integer(6), getValueByIndex(result.get(5), 0));
        assertEquals("xiaohong", getValueByIndex(result.get(5), 1));
        assertEquals(new Integer(7), getValueByIndex(result.get(6), 0));
        assertEquals("xiaored", getValueByIndex(result.get(6), 1));
        assertEquals(new Integer(8), getValueByIndex(result.get(7), 0));
        assertEquals("xiaohei", getValueByIndex(result.get(7), 1));
    }

    @Test
    public void testInsertReject() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "wangwei").build());
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 5)
                .withString("NAME", "the line should be rejected as it's too long")
                .build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 6).withString("NAME", "gaoyan").build());
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 7)
                .withString("NAME", "the line should be rejected as it's too long")
                .build());
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 8).withString("NAME", "dabao").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT);
        config.setUseBatch(false);
        config.setCommitEvery(DBTestUtils.randomInt());

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        assertEquals(Arrays.asList(records.get(0), records.get(2), records.get(4)),
                outputs.get(Record.class, Branches.DEFAULT_BRANCH));
        assertEquals(2, outputs.get(Record.class, "reject").size());

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(6, result.size());
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("wangwei", getValueByIndex(result.get(3), 1));
        assertEquals(new Integer(6), getValueByIndex(result.get(4), 0));
        assertEquals("gaoyan", getValueByIndex(result.get(4), 1));
        assertEquals(new Integer(8), getValueByIndex(result.get(5), 0));
        assertEquals("dabao", getValueByIndex(result.get(5), 1));
    }

    // TODO update action need key info, but that info only can get from input record now,
    // can't get from current component design schema as studio don't pass any schema info when dynamic column exists,
    // is right?
    // image, input component not set key info, but current output component set it, then no key info, then error, not a
    // bug?
    @Test
    public void testDynamicUpdate() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());

        List<SchemaInfo> schemaInfos = DBTestUtils.createTestDynamicMixSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.UPDATE);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(3, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
    }

    @Test
    public void testUpdate() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.UPDATE);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        assertEquals(records, outputs.get(Record.class, Branches.DEFAULT_BRANCH));

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(3, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
    }

    @Test
    public void testUpdateReject() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 1)
                .withString("NAME", "the line should be rejected as it's too long")
                .build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "newkey").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 5)
                .withString("NAME",
                        "the line is not rejected though it's too long as key not matched when update action")
                .build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 3).withString("NAME", "dabao1").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.UPDATE);
        config.setUseBatch(false);
        config.setCommitEvery(DBTestUtils.randomInt());

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        // if not update that row, the jdbc not throw exception? TODO check it, it depend on jdbc implement
        assertEquals(Arrays.asList(records.get(1), records.get(2), records.get(3), records.get(4)),
                outputs.get(Record.class, Branches.DEFAULT_BRANCH));
        assertEquals(1, outputs.get(Record.class, "reject").size());

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(3, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao1", getValueByIndex(result.get(2), 1));
    }

    @Test
    public void testDynamicDelete() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).build());
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).build());

        List<SchemaInfo> schemaInfos = DBTestUtils.createTestDynamicMixSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.DELETE);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(1, result.size());
        assertEquals(new Integer(3), getValueByIndex(result.get(0), 0));
        assertEquals("dabao", getValueByIndex(result.get(0), 1));
    }

    @Test
    public void testDelete() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).build());
        records.add(recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.DELETE);
        config.setDieOnError(true);

        randomBatchAndCommit(config);

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(1, result.size());
        assertEquals(new Integer(3), getValueByIndex(result.get(0), 0));
        assertEquals("dabao", getValueByIndex(result.get(0), 1));
    }

    // TODO how to make a delete action reject happen?
    @Test
    public void testDeleteReject() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "newkey").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 5)
                .withString("NAME",
                        "the line is not rejected though it's too long as only key is used by deleting action")
                .build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 3).withString("NAME", "dabao1").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.DELETE);
        config.setUseBatch(false);
        config.setCommitEvery(DBTestUtils.randomInt());

        BaseComponentsHandler.Outputs outputs = DBTestUtils.runProcessor(records, componentsHandler, config);
        assertEquals(records, outputs.get(Record.class, Branches.DEFAULT_BRANCH));
        assertNull(outputs.get(Record.class, "reject"));

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(0, result.size());
    }

    @Test
    public void testDynamicInsertOrUpdate() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "new one").build());

        List<SchemaInfo> schemaInfos = DBTestUtils.createTestDynamicMixSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT_OR_UPDATE);
        config.setDieOnError(true);

        config.setCommitEvery(DBTestUtils.randomInt());

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(4, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("new one", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void testInsertOrUpdate() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "new one").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.INSERT_OR_UPDATE);
        config.setDieOnError(true);

        config.setCommitEvery(DBTestUtils.randomInt());

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(4, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("new one", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void testDynamicUpdateOrInsert() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "new one").build());

        List<SchemaInfo> schemaInfos = DBTestUtils.createTestDynamicMixSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.UPDATE_OR_INSERT);
        config.setDieOnError(true);

        config.setCommitEvery(DBTestUtils.randomInt());

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(4, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("new one", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void testUpdateOrInsert() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 1).withString("NAME", "wangwei1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 2).withString("NAME", "gaoyan1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "new one").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        config.setDataAction(DataAction.UPDATE_OR_INSERT);
        config.setDieOnError(true);

        config.setCommitEvery(DBTestUtils.randomInt());

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        assertEquals(4, result.size());
        assertEquals(new Integer(1), getValueByIndex(result.get(0), 0));
        assertEquals("wangwei1", getValueByIndex(result.get(0), 1));
        assertEquals(new Integer(2), getValueByIndex(result.get(1), 0));
        assertEquals("gaoyan1", getValueByIndex(result.get(1), 1));
        assertEquals(new Integer(3), getValueByIndex(result.get(2), 0));
        assertEquals("dabao", getValueByIndex(result.get(2), 1));
        assertEquals(new Integer(4), getValueByIndex(result.get(3), 0));
        assertEquals("new one", getValueByIndex(result.get(3), 1));
    }

    @Test
    public void testClearDataInTable() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        DataAction action = DBTestUtils.randomDataAction();
        config.setDataAction(action);
        config.setDieOnError(DBTestUtils.randomBoolean());

        config.setClearData(true);

        randomBatchAndCommit(config);

        DBTestUtils.runOutput(records, componentsHandler, config);

        List<Record> result = DBTestUtils.runInput(componentsHandler, dataStore, tableName, schemaInfos);

        if (action == DataAction.INSERT || action == DataAction.INSERT_OR_UPDATE
                || action == DataAction.UPDATE_OR_INSERT) {
            assertEquals(2, result.size());
            assertEquals(new Integer(4), getValueByIndex(result.get(0), 0));
            assertEquals("xiaoming", getValueByIndex(result.get(0), 1));
            assertEquals(new Integer(5), getValueByIndex(result.get(1), 0));
            assertEquals("xiaobai", getValueByIndex(result.get(1), 1));
        } else {
            assertEquals(0, result.size());
        }
    }

    private String randomBatchAndCommit(JDBCOutputConfig config) {
        config.setUseBatch(DBTestUtils.randomBoolean());
        config.setBatchSize(DBTestUtils.randomInt());
        config.setCommitEvery(DBTestUtils.randomInt());
        return new StringBuilder().append("useBatch: ")
                .append(config.isUseBatch())
                .append(", batchSize: ")
                .append(config.getBatchSize())
                .append(", commitEvery:")
                .append(config.getCommitEvery())
                .toString();
    }

    @Test
    public void testDieOnError() {
        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(recordBuilderFactory.newRecordBuilder(schema)
                .withInt("ID", 5)
                .withString("NAME", "too long value")
                .build());

        List<SchemaInfo> schemaInfos = createTestSchemaInfos();

        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet4Output = new JDBCTableDataSet();
        dataSet4Output.setTableName(tableName);
        dataSet4Output.setDataStore(dataStore);
        dataSet4Output.setSchema(schemaInfos);
        config.setDataSet(dataSet4Output);

        DataAction action = DBTestUtils.randomDataActionExceptDeleteAndUpdate();
        config.setDataAction(action);
        config.setDieOnError(true);

        config.setUseBatch(DBTestUtils.randomBoolean());
        config.setBatchSize(DBTestUtils.randomInt());

        // we set it like this to avoid the dead lock when this case :
        // when die on error and not auto commit mode, we throw the exception, but not call commit or rollback in the
        // finally
        // part, it may make the dead lock for derby
        // in all the javajet db components, we have this issue too, but different db, different result, in my view, we
        // should
        // process
        // it, will create another test to show the dead lock issue for derby
        config.setCommitEvery(0);// or set value to 0 mean use the default commit mode

        try {
            DBTestUtils.runOutput(records, componentsHandler, config);
            log.info("assert fail for data action : " + config.getDataAction());
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    public void testWriterWithCloudStyle() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleAndUpdate() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setDataAction(DataAction.UPDATE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleAndDesignSchemaAndUpdate() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setDataAction(DataAction.UPDATE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleAndDesignSchemaAndDelete() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setDataAction(DataAction.DELETE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleAndDesignSchemaAndUpsert() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setDataAction(DataAction.INSERT_OR_UPDATE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleWithCreateTable() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("testCreateTable1");

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleWithCreateTableAndDesignSchema() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("testCreateTable2");
        // in the design schema, it contains size/scale, then can help create table
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Test
    public void testWriterWithCloudStyleWithDesignSchema() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Disabled
    @Test
    public void testWriterWithCloudStyleWithSnowflakeAndDesignSchema() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleSnowflakeDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai1").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Disabled
    @Test
    public void testWriterWithCloudStyleWithSnowflakeAndDesignSchemaAndUpdate() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleSnowflakeDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);
        config.setDataAction(DataAction.UPDATE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai1").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Disabled
    @Test
    public void testWriterWithCloudStyleWithSnowflakeAndDesignSchemaAndDelete() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleSnowflakeDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);
        config.setDataAction(DataAction.DELETE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming1").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai1").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

    @Disabled
    @Test
    public void testWriterWithCloudStyleWithSnowflakeAndDesignSchemaAndUpsert() {
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(DBTestUtils.createCloudStyleSnowflakeDataStore(true));
        dataSet.setTableName("test");
        dataSet.setSchema(DBTestUtils.createTestSchemaInfos());

        JDBCOutputConfig config = new JDBCOutputConfig();
        config.setDataSet(dataSet);
        config.setCreateTableIfNotExists(true);
        config.setDataAction(DataAction.INSERT_OR_UPDATE);

        Schema schema = DBTestUtils.createTestSchema(recordBuilderFactory);
        List<Record> records = new ArrayList<>();
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 4).withString("NAME", "xiaoming").build());
        records.add(
                recordBuilderFactory.newRecordBuilder(schema).withInt("ID", 5).withString("NAME", "xiaobai").build());

        DBTestUtils.runOutput(records, componentsHandler, config);
    }

}
