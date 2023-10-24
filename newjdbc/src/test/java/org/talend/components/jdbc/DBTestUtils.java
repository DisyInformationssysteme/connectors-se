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

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.AuthenticationType;
import org.talend.components.jdbc.common.PreparedStatementParameter;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.dataset.JDBCQueryDataSet;
import org.talend.components.jdbc.dataset.JDBCTableDataSet;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.input.JDBCCommonInputConfig;
import org.talend.components.jdbc.input.JDBCInputConfig;
import org.talend.components.jdbc.input.JDBCTableInputConfig;
import org.talend.components.jdbc.output.DataAction;
import org.talend.components.jdbc.output.JDBCOutputConfig;
import org.talend.components.jdbc.output.OutputProcessor;
import org.talend.components.jdbc.row.JDBCRowConfig;
import org.talend.components.jdbc.row.JDBCRowProcessor;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.components.jdbc.sp.JDBCSPConfig;
import org.talend.components.jdbc.sp.JDBCSPProcessor;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit.MainInputFactory;
import org.talend.sdk.component.runtime.base.Delegated;
import org.talend.sdk.component.runtime.manager.chain.AutoChunkProcessor;
import org.talend.sdk.component.runtime.manager.chain.Job;
import org.talend.sdk.component.runtime.output.OutputFactory;
import org.talend.sdk.component.runtime.output.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Date;
import java.sql.*;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

@Slf4j
public class DBTestUtils {

    static List<SchemaInfo> createTestDynamicSchemaInfos() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("DYN", null, true, "VARCHAR", "id_Dynamic", true, null, null, null, null, null));
        return schemaInfos;
    }

    static List<SchemaInfo> createTestDynamicMixSchemaInfos() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("ID", "ID", true, "INT", "id_Integer", false, null, 10, null, null, null));
        schemaInfos
                .add(new SchemaInfo("DYN", null, false, "VARCHAR", "id_Dynamic", true, null, null, null, null, null));
        return schemaInfos;
    }

    static List<SchemaInfo> createTestSchemaInfosWithResultSet() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("ID", "ID", true, "INT", "id_Integer", false, null, 10, null, null, null));
        schemaInfos
                .add(new SchemaInfo("NAME", "NAME", false, "VARCHAR", "id_String", true, null, 64, null, null, null));
        schemaInfos
                .add(new SchemaInfo("RESULTSET", null, false, null, "id_Object", true, null, null, null, null, null));
        return schemaInfos;
    }

    static List<SchemaInfo> createTestSchemaInfos() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("ID", "ID", true, "INT", "id_Integer", false, null, 10, null, null, null));
        schemaInfos
                .add(new SchemaInfo("NAME", "NAME", false, "VARCHAR", "id_String", true, null, 64, null, null, null));
        return schemaInfos;
    }

    static List<SchemaInfo> createTestSchemaInfos4RowResultSet() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos
                .add(new SchemaInfo("RESULTSET", null, false, null, "id_Object", true, null, null, null, null, null));
        return schemaInfos;
    }

    static Object getValueByIndex(Record record, int index) {
        return record.get(Object.class, record.getSchema().getEntries().get(index));
    }

    static List<Record> runInput(BaseComponentsHandler componentsHandler, JDBCDataStore dataStore, String tableName,
            List<SchemaInfo> schemaInfos) {
        JDBCInputConfig inputConfig = new JDBCInputConfig();
        JDBCQueryDataSet dataset4Input = new JDBCQueryDataSet();
        dataset4Input.setDataStore(dataStore);
        dataset4Input.setSqlQuery(DBTestUtils.getSQL(tableName));
        dataset4Input.setSchema(schemaInfos);
        inputConfig.setDataSet(dataset4Input);
        inputConfig.setConfig(new JDBCCommonInputConfig());

        List<Record> result = DBTestUtils.runInput(componentsHandler, inputConfig);
        return result;
    }

    static BaseComponentsHandler.Outputs runProcessor(List<Record> input, BaseComponentsHandler componentsHandler,
            JDBCRowConfig config, List<Object> preparedValues) {
        final Processor processor = componentsHandler.createProcessor(JDBCRowProcessor.class, config);

        fillPreparedValues(preparedValues, processor);

        final BaseComponentsHandler.Outputs outputs =
                componentsHandler.collect(processor, new MainInputFactory(input.iterator()));

        return outputs;
    }

    private static void fillPreparedValues(List<Object> preparedValues, Processor processor) {
        if (preparedValues == null || preparedValues.isEmpty())
            return;

        JDBCRowProcessor delegated = JDBCRowProcessor.class.cast(Delegated.class.cast(processor).getDelegate());
        try {
            Field field = delegated.getClass().getDeclaredField("configuration");
            field.setAccessible(true);
            JDBCRowConfig deser_config = JDBCRowConfig.class.cast(field.get(delegated));
            if (deser_config.isUsePreparedStatement()) {
                List<PreparedStatementParameter> list = deser_config.getPreparedStatementParameters();
                for (int i = 0; i < list.size(); i++) {
                    PreparedStatementParameter p = list.get(i);
                    p.setDataValue(preparedValues.get(i));
                }
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    static Map<String, List<?>> runProcessorWithoutInput(BaseComponentsHandler componentsHandler, JDBCRowConfig config,
            List<Object> preparedValues) {
        final Processor processor = componentsHandler.createProcessor(JDBCRowProcessor.class, config);

        fillPreparedValues(preparedValues, processor);

        Map<String, List<?>> data = execute(processor);

        return data;
    }

    static Map<String, List<?>> runProcessorWithoutInput(BaseComponentsHandler componentsHandler, JDBCSPConfig config) {
        final Processor processor = componentsHandler.createProcessor(JDBCSPProcessor.class, config);

        Map<String, List<?>> data = execute(processor);

        return data;
    }

    private static Map<String, List<?>> execute(Processor processor) {
        MainInputFactory inputs = new MainInputFactory(Collections.emptyIterator());
        AutoChunkProcessor autoChunkProcessor = new AutoChunkProcessor(10, processor);
        autoChunkProcessor.start();
        Map<String, List<?>> data = new HashMap<>();
        OutputFactory outputFactory = (name) -> {
            return (value) -> {
                List aggregator = (List) data.computeIfAbsent(name, (n) -> {
                    return new ArrayList();
                });
                aggregator.add(value);
            };
        };

        try {
            autoChunkProcessor.onElement(inputs, outputFactory);

            autoChunkProcessor.flush(outputFactory);
        } finally {
            autoChunkProcessor.stop();
        }
        return data;
    }

    static List<Record> runInput(BaseComponentsHandler componentsHandler, JDBCInputConfig config) {
        String inConfig = configurationByExample().forInstance(config).configured().toQueryString();
        Job.components()
                .component("in", "JDBC://Input?" + inConfig)
                .component("out", "test://collector")
                .connections()
                .from("in")
                .to("out")
                .build()
                .run();
        List<Record> data = new ArrayList<>(componentsHandler.getCollectedData(Record.class));
        componentsHandler.resetState();

        return data;
    }

    static List<Record> runInput(BaseComponentsHandler componentsHandler, JDBCTableInputConfig config) {
        String inConfig = configurationByExample().forInstance(config).configured().toQueryString();
        Job.components()
                .component("in", "JDBC://TableInput?" + inConfig)
                .component("out", "test://collector")
                .connections()
                .from("in")
                .to("out")
                .build()
                .run();
        List<Record> data = new ArrayList<>(componentsHandler.getCollectedData(Record.class));
        componentsHandler.resetState();

        return data;
    }

    static void runOutput(List<Record> input, BaseComponentsHandler componentsHandler, JDBCOutputConfig config) {
        componentsHandler.setInputData(input);
        String outConfig = configurationByExample().forInstance(config).configured().toQueryString();
        Job.components()
                .component("in", "test://emitter")
                .component("out", "JDBC://Output?" + outConfig)
                .connections()
                .from("in")
                .to("out")
                .build()
                .run();
        componentsHandler.resetState();
    }

    static BaseComponentsHandler.Outputs runProcessor(List<Record> input, BaseComponentsHandler componentsHandler,
            JDBCOutputConfig config) {
        final Processor processor = componentsHandler.createProcessor(OutputProcessor.class, config);
        final BaseComponentsHandler.Outputs outputs =
                componentsHandler.collect(processor, new MainInputFactory(input.iterator()));

        return outputs;
    }

    static BaseComponentsHandler.Outputs runProcessor(List<Record> input, BaseComponentsHandler componentsHandler,
            JDBCSPConfig config) {
        final Processor processor = componentsHandler.createProcessor(JDBCSPProcessor.class, config);
        final BaseComponentsHandler.Outputs outputs =
                componentsHandler.collect(processor, new MainInputFactory(input.iterator()));

        return outputs;
    }

    public static void shutdownDBIfNecessary() {
    }

    public static void createTestTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("create table " + tableName + " (ID int, NAME varchar(8))");
        }
    }

    public static void dropTestTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("drop table " + tableName);
        }
    }

    public static void truncateTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("delete from " + tableName);
        }
    }

    public static void loadTestData(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("insert into " + tableName + " values(?,?)")) {
            statement.setInt(1, 1);
            statement.setString(2, "wangwei");

            statement.executeUpdate();

            statement.setInt(1, 2);
            statement.setString(2, " gaoyan ");

            statement.executeUpdate();

            statement.setInt(1, 3);
            statement.setString(2, "dabao");

            statement.executeUpdate();
        }

        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    public static Schema createTestSchema(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("ID")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .withProp(SchemaProperty.IS_KEY, "true")
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("NAME")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .build();
    }

    /**
     * Following several methods are setup and tearDown methods for all types table.
     * all types tables contains columns for each data type available in Derby DB
     * This is required to test conversion between SQL -> JDBC -> Avro data types
     */
    public static void createAllTypesTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("create table " + tableName
                    + " (SMALL_INT_COL smallint, INT_COL integer, BIG_INT_COL bigint, REAL_COL real, DOUBLE_COL double,"
                    + "DECIMAL_COL decimal(20,10), CHAR_COL char(4), VARCHAR_COL varchar(8), BLOB_COL blob(16), CLOB_COL clob(16), DATE_COL date,"
                    + "TIME_COL time, TIMESTAMP_COL timestamp, BOOLEAN_COL boolean)");
        }
    }

    public static void dropAllTypesTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("drop table " + tableName);
        }
    }

    public static void truncateAllTypesTable(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("delete from " + tableName);
        }
    }

    /**
     * Load only one record
     */
    public static void loadAllTypesData(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement statement = conn
                .prepareStatement("insert into " + tableName + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setShort(1, (short) 32767);
            statement.setInt(2, 2147483647);
            statement.setLong(3, 9223372036854775807l);
            statement.setFloat(4, 1.11111111f);
            statement.setDouble(5, 2.222222222);
            statement.setBigDecimal(6, new BigDecimal("1234567890.1234567890"));
            statement.setString(7, "abcd");
            statement.setString(8, "abcdefg");

            Blob blob = conn.createBlob();
            byte[] bytes = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
            blob.setBytes(1, bytes);
            statement.setBlob(9, blob);

            Clob clob = conn.createClob();
            clob.setString(1, "abcdefg");
            statement.setClob(10, clob);

            statement.setDate(11, Date.valueOf("2016-12-28"));
            statement.setTime(12, Time.valueOf("14:30:33"));
            statement.setTimestamp(13, Timestamp.valueOf("2016-12-28 14:31:56.12345"));
            statement.setBoolean(14, true);

            statement.executeUpdate();
        }

        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    public static Schema createAllTypesSchema(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("SMALL_INT_COL")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("INT_COL")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("BIG_INT_COL")
                        .withType(Schema.Type.LONG)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("REAL_COL")
                        .withType(Schema.Type.FLOAT)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("DOUBLE_COL")
                        .withType(Schema.Type.DOUBLE)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("DECIMAL_COL")
                        .withType(Schema.Type.DECIMAL)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("CHAR_COL")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("VARCHAR_COL")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("BLOB_COL")
                        .withType(Schema.Type.BYTES)
                        .withNullable(true)
                        .build())// TODO fix it
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("CLOB_COL")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())// TODO fix it
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("DATE_COL")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("TIME_COL")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("TIMESTAMP_COL")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("BOOLEAN_COL")
                        .withType(Schema.Type.BOOLEAN)
                        .withNullable(true)
                        .build())
                .build();
    }

    public static int countItemsInTable(String tableName, Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement("select count(*) from " + tableName);
        ResultSet rs = preparedStatement.executeQuery();
        rs.next();
        return rs.getInt(1);
    }

    public static void createTableForEveryType(JDBCService service, JDBCDataStore dataStore, String tableName)
            throws SQLException {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            createTestTableForEveryType(conn, tableName);
        }
    }

    public static void createTableWithSpecialName(Connection conn, String tablename)
            throws SQLException, ClassNotFoundException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE " + tablename + " (P1_Vente_Qté INT, \"Customer Id\" INT)");
        }
    }

    public static void truncateTableAndLoadDataForEveryType(JDBCService service, JDBCDataStore dataStore,
            String tableName)
            throws SQLException, ClassNotFoundException {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            truncateTable(conn, tableName);
            loadTestDataForEveryType(conn, tableName);
        }
    }

    private static void loadTestDataForEveryType(Connection conn, String tablename) throws SQLException {
        try (PreparedStatement statement = conn
                .prepareStatement("insert into " + tablename + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setInt(1, 1);
            statement.setShort(2, (short) 2);
            statement.setLong(3, 3l);
            statement.setFloat(4, 4f);
            statement.setDouble(5, 5d);
            statement.setFloat(6, 6f);
            statement.setBigDecimal(7, new BigDecimal("7.01"));
            statement.setBigDecimal(8, new BigDecimal("8.01"));
            statement.setBoolean(9, true);
            statement.setString(10, "the first char value");
            long currentTimeMillis = System.currentTimeMillis();
            statement.setTimestamp(11, new Timestamp(currentTimeMillis));
            statement.setTimestamp(12, new Timestamp(currentTimeMillis));
            statement.setTimestamp(13, new Timestamp(currentTimeMillis));
            statement.setString(14, "wangwei");
            statement.setString(15, "a long one : 1");
            statement.setNull(16, java.sql.Types.BLOB);
            statement.executeUpdate();

            statement.setInt(1, 1);
            statement.setShort(2, (short) 2);
            statement.setLong(3, 3l);
            statement.setFloat(4, 4f);
            statement.setDouble(5, 5d);
            statement.setFloat(6, 6f);
            statement.setBigDecimal(7, new BigDecimal("7.01"));
            statement.setBigDecimal(8, new BigDecimal("8.01"));
            statement.setBoolean(9, true);
            statement.setString(10, "the second char value");
            statement.setTimestamp(11, new Timestamp(currentTimeMillis));
            statement.setTimestamp(12, new Timestamp(currentTimeMillis));
            statement.setTimestamp(13, new Timestamp(currentTimeMillis));
            statement.setString(14, "gaoyan");
            statement.setString(15, "a long one : 2");
            statement.setNull(16, java.sql.Types.BLOB);
            statement.executeUpdate();

            statement.setInt(1, 1);
            statement.setShort(2, (short) 2);
            statement.setLong(3, 3l);
            statement.setFloat(4, 4f);
            statement.setDouble(5, 5d);
            statement.setFloat(6, 6f);
            statement.setBigDecimal(7, new BigDecimal("7.01"));
            statement.setBigDecimal(8, new BigDecimal("8.01"));
            statement.setBoolean(9, true);
            statement.setString(10, "the third char value");
            statement.setTimestamp(11, new Timestamp(currentTimeMillis));
            statement.setTimestamp(12, new Timestamp(currentTimeMillis));
            statement.setTimestamp(13, new Timestamp(currentTimeMillis));
            statement.setString(14, "dabao");
            statement.setString(15, "a long one : 3");
            statement.setNull(16, java.sql.Types.BLOB);
            statement.executeUpdate();

            // used by testing the null value
            statement.setInt(1, 1);
            statement.setNull(2, java.sql.Types.SMALLINT);
            statement.setNull(3, java.sql.Types.BIGINT);
            statement.setNull(4, java.sql.Types.FLOAT);
            statement.setNull(5, java.sql.Types.DOUBLE);
            statement.setNull(6, java.sql.Types.FLOAT);
            statement.setNull(7, java.sql.Types.DECIMAL);
            statement.setNull(8, java.sql.Types.DECIMAL);
            statement.setNull(9, java.sql.Types.BOOLEAN);
            statement.setNull(10, java.sql.Types.CHAR);
            statement.setNull(11, java.sql.Types.DATE);
            statement.setNull(12, java.sql.Types.TIME);
            statement.setNull(13, java.sql.Types.TIMESTAMP);
            statement.setNull(14, java.sql.Types.VARCHAR);
            statement.setNull(15, java.sql.Types.LONGVARCHAR);
            statement.setNull(16, java.sql.Types.BLOB);
            statement.executeUpdate();

            statement.setNull(1, java.sql.Types.INTEGER);
            statement.setNull(2, java.sql.Types.SMALLINT);
            statement.setNull(3, java.sql.Types.BIGINT);
            statement.setNull(4, java.sql.Types.FLOAT);
            statement.setNull(5, java.sql.Types.DOUBLE);
            statement.setNull(6, java.sql.Types.FLOAT);
            statement.setNull(7, java.sql.Types.DECIMAL);
            statement.setNull(8, java.sql.Types.DECIMAL);
            statement.setNull(9, java.sql.Types.BOOLEAN);
            statement.setNull(10, java.sql.Types.CHAR);
            statement.setNull(11, java.sql.Types.DATE);
            statement.setNull(12, java.sql.Types.TIME);
            statement.setNull(13, java.sql.Types.TIMESTAMP);
            statement.setString(14, "good luck");
            statement.setNull(15, java.sql.Types.LONGVARCHAR);
            statement.setNull(16, java.sql.Types.BLOB);
            statement.executeUpdate();
        }

        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    // TODO : now we have to use the type for derby to test, should use the common one for every database or write it
    // for every
    // database
    private static void createTestTableForEveryType(Connection conn, String tablename) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE " + tablename
                    + " (C1 INT, C2 SMALLINT, C3 BIGINT, C4 REAL,C5 DOUBLE, C6 FLOAT, C7 DECIMAL(10,2), C8 NUMERIC(10,2), C9 BOOLEAN, C10 CHAR(64), C11 DATE, C12 TIME, C13 TIMESTAMP, C14 VARCHAR(64), C15 LONG VARCHAR, C16 BLOB(16M))");
        }
    }

    public static Schema createTestSchemaForAllDBType(RecordBuilderFactory recordBuilderFactory,
            boolean nullableForAnyColumn) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C1")
                        .withType(Schema.Type.INT)
                        .withNullable(nullableForAnyColumn)
                        .build())// TODO set key
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C2")
                        .withType(Schema.Type.INT)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C3")
                        .withType(Schema.Type.LONG)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C4")
                        .withType(Schema.Type.FLOAT)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C5")
                        .withType(Schema.Type.DOUBLE)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C6")
                        .withType(Schema.Type.FLOAT)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C7")
                        .withType(Schema.Type.DECIMAL)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C8")
                        .withType(Schema.Type.DECIMAL)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C9")
                        .withType(Schema.Type.BOOLEAN)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C10")
                        .withType(Schema.Type.STRING)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C11")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C12")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C13")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C14")
                        .withType(Schema.Type.STRING)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C15")
                        .withType(Schema.Type.STRING)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("C16")
                        .withType(Schema.Type.BYTES)
                        .withNullable(nullableForAnyColumn)
                        .build())
                .build();
    }

    public static List<Record> prepareIndexRecords(RecordBuilderFactory recordBuilderFactory,
            boolean nullableForAnyColumn) {
        List<Record> result = new ArrayList<Record>();

        Schema schema = createTestSchemaForAllDBType(recordBuilderFactory, nullableForAnyColumn);

        byte[] blob = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

        List<List<Object>> contents = new ArrayList<>();
        contents.add(Arrays.asList(1, (short) 2, 3l, 4f, 5d, 6f, new BigDecimal("7.01"), new BigDecimal("8.01"), true,
                "content : 1", new java.util.Date(), new java.util.Date(), new java.util.Date(), "wangwei",
                "long content : 1", blob));
        contents.add(Arrays.asList(1, (short) 2, 3l, 4f, 5d, 6f, new BigDecimal("7.01"), new BigDecimal("8.01"), true,
                "content : 2", new java.util.Date(), new java.util.Date(), new java.util.Date(), "gaoyan",
                "long content : 2", blob));
        contents.add(Arrays.asList(1, (short) 2, 3l, 4f, 5d, 6f, new BigDecimal("7.01"), new BigDecimal("8.01"), true,
                "content : 3", new java.util.Date(), new java.util.Date(), new java.util.Date(), "dabao",
                "long content : 3", blob));
        contents.add(Arrays.asList(1, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, blob));
        contents.add(Arrays.asList(null, null, null, null, null, null, null, null, null, null, null, null, null,
                "good luck", null, blob));

        contents.stream().forEach(content -> {
            Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schema);
            int i = 0;
            for (Schema.Entry entry : schema.getEntries()) {
                recordBuilder.with(entry, content.get(i++));
            }
            result.add(recordBuilder.build());
        });

        return result;
    }

    private static Record copyValueFrom(RecordBuilderFactory recordBuilderFactory, Record record) {
        Schema schema = record.getSchema();
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schema);
        int i = 0;
        for (Schema.Entry entry : schema.getEntries()) {
            recordBuilder.with(entry, record.get(Object.class, entry));
        }

        return recordBuilder.build();
    }

    private static Random random = new Random();

    public static boolean randomBoolean() {
        return random.nextBoolean();
    }

    public static int randomInt() {
        return random.nextInt(5) + 1;
    }

    public static DataAction randomDataAction() {
        int value = random.nextInt(5);
        switch (value) {
        case 0:
            return DataAction.INSERT;
        case 1:
            return DataAction.UPDATE;
        case 2:
            return DataAction.DELETE;
        case 3:
            return DataAction.INSERT_OR_UPDATE;
        case 4:
            return DataAction.UPDATE_OR_INSERT;
        default:
            return DataAction.INSERT;
        }
    }

    public static DataAction randomDataActionExceptDeleteAndUpdate() {
        int value = random.nextInt(3);
        switch (value) {
        case 0:
            return DataAction.INSERT;
        case 1:
            return DataAction.INSERT_OR_UPDATE;
        case 2:
            return DataAction.UPDATE_OR_INSERT;
        default:
            return DataAction.INSERT;
        }
    }

    public static Schema createTestSchema4(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("RESULTSET")
                        .withType(Schema.Type.STRING)
                        .withProp("", "")
                        .withNullable(true)
                        .build())// TODO set it as studio object type
                .build();
    }

    public static Schema createTestSchema5(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("ID")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("NAME")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("RESULTSET")
                        .withType(Schema.Type.STRING)
                        .withProp("", "")
                        .withNullable(true)
                        .build())// TODO set it as studio object type
                .build();
    }

    public static JDBCOutputConfig createCommonJDBCOutputConfig(JDBCDataStore dataStore) {
        JDBCOutputConfig config = new JDBCOutputConfig();
        JDBCTableDataSet dataSet = new JDBCTableDataSet();
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        return config;
    }

    public static JDBCInputConfig createCommonJDBCInputConfig(JDBCDataStore dataStore) {
        JDBCInputConfig config = new JDBCInputConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        return config;
    }

    public static JDBCRowConfig createCommonJDBCRowConfig(JDBCDataStore dataStore) {
        JDBCRowConfig config = new JDBCRowConfig();
        JDBCQueryDataSet dataSet = new JDBCQueryDataSet();
        dataSet.setDataStore(dataStore);
        config.setDataSet(dataSet);
        return config;
    }

    private static java.util.Properties props = null;

    public static JDBCDataStore createDataStore(boolean autoCommit) throws IOException {
        if (props == null) {
            try (InputStream is = DBTestUtils.class.getClassLoader().getResourceAsStream("connection.properties")) {
                props = new java.util.Properties();
                props.load(is);
            }
        }

        final String driverClass = props.getProperty("driverClass");

        final String jdbcUrl = props.getProperty("jdbcUrl");

        final String userId = props.getProperty("userId");

        final String password = props.getProperty("password");

        final JDBCDataStore dataStore = new JDBCDataStore();

        dataStore.setJdbcClass(driverClass);
        dataStore.setJdbcUrl(jdbcUrl);
        dataStore.setUserId(userId);
        dataStore.setPassword(password);

        dataStore.setUseAutoCommit(true);
        dataStore.setAutoCommit(autoCommit);

        return dataStore;
    }

    // only for env special test, don't want to introduce the slow docker
    public static JDBCDataStore createCloudStyleDataStore(boolean autoCommit) {
        final JDBCDataStore dataStore = new JDBCDataStore();

        dataStore.setDbType("MySQL");
        dataStore.setSetRawUrl(false);
        dataStore.setHost("localhost");
        dataStore.setPort(32768);
        dataStore.setDatabase("test");
        dataStore.setUserId("");
        dataStore.setPassword("");

        dataStore.setUseAutoCommit(true);
        dataStore.setAutoCommit(autoCommit);

        return dataStore;
    }

    public static JDBCDataStore createCloudStyleSnowflakeDataStore(boolean autoCommit) {
        final JDBCDataStore dataStore = new JDBCDataStore();

        dataStore.setDbType("Snowflake");
        dataStore.setSetRawUrl(true);
        dataStore.setJdbcUrl("");
        dataStore.setAuthenticationType(AuthenticationType.BASIC);
        dataStore.setUserId("");
        dataStore.setPassword("");

        dataStore.setUseAutoCommit(true);
        dataStore.setAutoCommit(autoCommit);

        return dataStore;
    }

    public static String getSQL(String tablename) {
        return "select * from " + tablename;
    }

    public static void testMetadata(List<Schema.Entry> columns) {
        Schema.Entry field = columns.get(0);

        assertEquals("ID", field.getOriginalFieldName());
        assertEquals(Schema.Type.INT, field.getType());
        // assertEquals("INTEGER", field.getProp(""));//TODO origin db type
        assertEquals(null, field.getProp(SchemaProperty.SIZE));
        assertEquals(null, field.getProp(SchemaProperty.SCALE));// TODO scale
        assertEquals(null, field.getProp(SchemaProperty.PATTERN));// TODO pattern
        assertEquals(null, field.getProp(""));// TODO default

        field = columns.get(1);

        assertEquals("NAME", field.getOriginalFieldName());
        assertEquals(Schema.Type.STRING, field.getType());
        // assertEquals("VARCHAR", field.getProp(""));//TODO origin db type
        assertEquals("8", field.getProp(SchemaProperty.SIZE));
        assertEquals(null, field.getProp(SchemaProperty.SCALE));
        assertEquals(null, field.getProp(SchemaProperty.PATTERN));// TODO pattern
        assertEquals(null, field.getProp(""));// TODO default
    }

    private static void createAllFunctionOrProcedures(JDBCService service, JDBCDataStore dataStore, String tableName)
            throws Exception {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            try (Statement statement = conn.createStatement()) {
                statement.execute("CREATE PROCEDURE p1 ()  INSERT INTO " + tableName + " values(4, 'lucky') ");// no in,
                                                                                                               // no out
                statement.execute("CREATE PROCEDURE p2 (IN a1 CHAR(20), IN a2 CHAR(20)) BEGIN INSERT INTO " + tableName
                        + " values(a1, a2); END");// only
                // in
                statement.execute(
                        "CREATE PROCEDURE p3 (OUT a1 INT) BEGIN SELECT COUNT(*) INTO a1 FROM " + tableName + "; END");// only
                // out
                statement.execute("CREATE PROCEDURE p4 (IN a1 CHAR(20), IN a2 CHAR(20), OUT a3 INT) BEGIN INSERT INTO "
                        + tableName + " values(a1, a2);SELECT COUNT(*) INTO a3 FROM " + tableName + "; END");// in
                // and
                // out
                statement
                        .execute(
                                "CREATE FUNCTION f1 (a CHAR(20)) RETURNS CHAR(50) DETERMINISTIC RETURN CONCAT('Hello, ',a,'!')");// function
            }
        }
    }

    private static void dropAllFunctionOrProcedures(JDBCService service, JDBCDataStore dataStore) throws SQLException {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = service.createDataSource(dataStore);
                Connection conn = dataSourceWrapper.getConnection()) {
            try (Statement statement = conn.createStatement()) {
                statement.execute("DROP PROCEDURE p1");
                statement.execute("DROP PROCEDURE p2");
                statement.execute("DROP PROCEDURE p3");
                statement.execute("DROP PROCEDURE p4");
                statement.execute("DROP FUNCTION f1");
            }
        }
    }

    public static List<SchemaInfo> createSPSchemaInfo1() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(
                new SchemaInfo("PARAMETER", "PARAMETER", false, "INT", "id_Integer", true, null, 10, null, null, null));
        return schemaInfos;
    }

    public static Schema createSPSchema1(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("PARAMETER")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .build())
                .build();
    }

    public static List<SchemaInfo> createSPSchemaInfo2() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("PARAMETER", "PARAMETER", false, "VARCHAR", "id_String", true, null, 10, null,
                null, null));
        return schemaInfos;
    }

    public static Schema createSPSchema2(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("PARAMETER")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .build();
    }

    public static List<SchemaInfo> createSPSchemaInfo3() {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        schemaInfos.add(new SchemaInfo("PARAMETER1", "PARAMETER1", false, "INT", "id_Integer", true, null, 10, null,
                null, null));
        schemaInfos.add(new SchemaInfo("PARAMETER2", "PARAMETER2", false, "VARCHAR", "id_String", true, null, 10, null,
                null, null));
        return schemaInfos;
    }

    public static Schema createSPSchema3(RecordBuilderFactory recordBuilderFactory) {
        return recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("PARAMETER1")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .build())
                .withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName("PARAMETER2")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build())
                .build();
    }

    public static JDBCSPConfig createCommonJDBCSPConfig(JDBCDataStore dataStore) {
        JDBCSPConfig config = new JDBCSPConfig();
        config.setDataStore(dataStore);
        return config;
    }

    public static URL correctURL(URL mappings_url) throws UnsupportedEncodingException, MalformedURLException {
        String file_path = URLDecoder.decode(mappings_url.getFile(), "UTF-8");
        mappings_url = new URL(mappings_url.getProtocol(), mappings_url.getHost(), mappings_url.getPort(), file_path);
        return mappings_url;
    }

    public static void testMetadata4SpecialName(List<Schema.Entry> columns) {
        Schema.Entry field = columns.get(0);

        assertEquals("P1_VENTE_QT_", field.getName());
        assertEquals("P1_VENTE_QTÉ", field.getOriginalFieldName());

        field = columns.get(1);
        assertEquals("Customer_Id", field.getName());
        assertEquals("Customer Id", field.getOriginalFieldName());
    }
}
