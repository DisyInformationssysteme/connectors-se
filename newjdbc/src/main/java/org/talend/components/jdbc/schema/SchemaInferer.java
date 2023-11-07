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
package org.talend.components.jdbc.schema;

import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.Date;

import static org.talend.sdk.component.api.record.Schema.Type.*;

public class SchemaInferer {

    public static Schema infer(RecordBuilderFactory recordBuilderFactory, ResultSetMetaData metadata, Dbms mapping,
            boolean allowSpecialName)
            throws SQLException {
        Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(RECORD);

        Set<String> existNames = new HashSet<>();
        int index = 0;

        int count = metadata.getColumnCount();
        for (int i = 1; i <= count; i++) {
            int size = metadata.getPrecision(i);
            int scale = metadata.getScale(i);
            boolean nullable = ResultSetMetaData.columnNullable == metadata.isNullable(i);

            int dbtype = metadata.getColumnType(i);
            String fieldName = metadata.getColumnLabel(i);
            String dbColumnName = metadata.getColumnName(i);

            // not necessary for the result schema from the query statement
            boolean isKey = false;

            String columnTypeName = metadata.getColumnTypeName(i).toUpperCase();

            // tck withName api can correct it auto, but not readable, so process it here
            String validName = NameUtil.correct(fieldName, index++, existNames);
            existNames.add(validName);

            Schema.Entry.Builder entryBuilder = sqlType2Tck(recordBuilderFactory, size, scale, dbtype, nullable,
                    validName, dbColumnName, null, isKey, mapping,
                    columnTypeName, false, false);

            schemaBuilder.withEntry(entryBuilder.build());
        }

        if (allowSpecialName) {
            schemaBuilder.withProp(SchemaProperty.ALLOW_SPECIAL_NAME, "true");
        }

        return schemaBuilder.build();
    }

    public static Schema infer(RecordBuilderFactory recordBuilderFactory, JDBCTableMetadata tableMetadata, Dbms mapping,
            boolean needUniqueColumnsAndForeignKeys, boolean allowSpecialName)
            throws SQLException {
        Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(RECORD);

        DatabaseMetaData databaseMetdata = tableMetadata.getDatabaseMetaData();

        final Set<String> keys =
                getPrimaryKeys(databaseMetdata, tableMetadata.getCatalog(), tableMetadata.getDbSchema(),
                        tableMetadata.getTablename());

        Set<String> uniqueColumns = null;
        Set<String> foreignKeys = null;
        if (needUniqueColumnsAndForeignKeys) {
            uniqueColumns = getUniqueColumns(databaseMetdata, tableMetadata.getCatalog(), tableMetadata.getDbSchema(),
                    tableMetadata.getTablename());
            foreignKeys = getForeignKeys(databaseMetdata, tableMetadata.getCatalog(), tableMetadata.getDbSchema(),
                    tableMetadata.getTablename());
        }

        Set<String> existNames = new HashSet<>();
        int index = 0;

        try (ResultSet metadata = databaseMetdata.getColumns(tableMetadata.getCatalog(), tableMetadata.getDbSchema(),
                tableMetadata.getTablename(), null)) {
            if (!metadata.next()) {
                return null;
            }

            String tablename = metadata.getString("TABLE_NAME");

            do {
                int size = metadata.getInt("COLUMN_SIZE");
                int scale = metadata.getInt("DECIMAL_DIGITS");
                int dbtype = metadata.getInt("DATA_TYPE");
                boolean nullable = DatabaseMetaData.columnNullable == metadata.getInt("NULLABLE");

                String columnName = metadata.getString("COLUMN_NAME");
                boolean isKey = keys.contains(columnName);

                boolean isUniqueColumn = false;
                boolean isForeignKey = false;
                if (needUniqueColumnsAndForeignKeys) {
                    // primary key also create unique index, so exclude it here
                    isUniqueColumn = isKey ? false : uniqueColumns.contains(columnName);
                    isForeignKey = foreignKeys.contains(columnName);
                }

                String defaultValue = metadata.getString("COLUMN_DEF");

                String columnTypeName = metadata.getString("TYPE_NAME");
                if (columnTypeName != null) {
                    columnTypeName = columnTypeName.toUpperCase();
                }

                // tck withName api can correct it auto, but not readable, so process it here
                String validName = NameUtil.correct(columnName, index++, existNames);
                existNames.add(validName);

                Schema.Entry.Builder entryBuilder = sqlType2Tck(recordBuilderFactory, size, scale, dbtype, nullable,
                        validName, columnName, defaultValue, isKey, mapping,
                        columnTypeName, isUniqueColumn, isForeignKey);

                schemaBuilder.withEntry(entryBuilder.build());
            } while (metadata.next());

            if (allowSpecialName) {
                schemaBuilder.withProp(SchemaProperty.ALLOW_SPECIAL_NAME, "true");
            }
            return schemaBuilder.build();
        }
    }

    private static Set<String> getPrimaryKeys(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getPrimaryKeys(catalogName, schemaName, tableName)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("COLUMN_NAME"));
                }
            }
        }

        return result;
    }

    private static Set<String> getUniqueColumns(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getIndexInfo(catalogName, schemaName, tableName, true, true)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    String indexColumn = resultSet.getString("COLUMN_NAME");
                    // some database return some null, for example oracle, so need this null check
                    if (indexColumn != null) {
                        result.add(indexColumn);
                    }
                }
            }
        }

        return result;
    }

    private static Set<String> getForeignKeys(DatabaseMetaData databaseMetdata, String catalogName, String schemaName,
            String tableName) throws SQLException {
        Set<String> result = new HashSet<>();

        try (ResultSet resultSet = databaseMetdata.getImportedKeys(catalogName, schemaName, tableName)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("FKCOLUMN_NAME"));
                }
            }
        }

        return result;
    }

    private static Schema.Entry.Builder sqlType2Tck(RecordBuilderFactory recordBuilderFactory, int size, int scale,
            int dbtype, boolean nullable, String name, String dbColumnName,
            Object defaultValue, boolean isKey, Dbms mapping, String columnTypeName, boolean isUniqueColumn,
            boolean isForeignKey) {
        final Schema.Entry.Builder entryBuilder = recordBuilderFactory.newEntryBuilder()
                .withName(name)
                .withRawName(dbColumnName)
                .withNullable(nullable)
                .withProp(SchemaProperty.IS_KEY, String.valueOf(isKey));

        if (isUniqueColumn) {
            entryBuilder.withProp(SchemaProperty.IS_UNIQUE, "true");
        }

        if (isForeignKey) {
            entryBuilder.withProp(SchemaProperty.IS_FOREIGN_KEY, "true");
        }

        boolean isIgnoreLength = false;
        boolean isIgnorePrecision = false;

        // TODO here need to consider four cases to run:
        // 1. studio job run
        // 2. studio metadata trigger
        // 3. studio component button trigger, seems same with 2?
        // 4. cloud platform which don't have mapping files

        // 2&3 are called by (http rest api) or (hard api implement code to call by component-server/runtime inside
        // code), need to consider

        final boolean noMappingFiles = mapping == null;

        // TODO refactor the logic, now only make it works, as we have other more important limit to fix like system
        // property to pass for studio component server

        if (noMappingFiles) {// by java sql type if not find talend db mapping files
            // TODO check more
            switch (dbtype) {
            case java.sql.Types.SMALLINT:
            case java.sql.Types.TINYINT:
            case java.sql.Types.INTEGER:
                entryBuilder.withType(INT).withProp(SchemaProperty.STUDIO_TYPE, TalendType.INTEGER.getName());
                // TODO process LONG by this : if (javaType.equals(Integer.class.getName()) ||
                // Short.class.getName().equals(javaType))
                break;
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
                entryBuilder.withType(FLOAT).withProp(SchemaProperty.STUDIO_TYPE, TalendType.FLOAT.getName());
                break;
            case java.sql.Types.DOUBLE:
                entryBuilder.withType(DOUBLE).withProp(SchemaProperty.STUDIO_TYPE, TalendType.DOUBLE.getName());
                break;
            case java.sql.Types.BOOLEAN:
                entryBuilder.withType(BOOLEAN).withProp(SchemaProperty.STUDIO_TYPE, TalendType.BOOLEAN.getName());
                break;
            case java.sql.Types.TIME:
                entryBuilder.withType(DATETIME)
                        .withProp(SchemaProperty.STUDIO_TYPE, TalendType.DATE.getName());
                break;
            case java.sql.Types.DATE:
                entryBuilder.withType(DATETIME)
                        .withProp(SchemaProperty.STUDIO_TYPE, TalendType.DATE.getName());
                break;
            case java.sql.Types.TIMESTAMP:
                entryBuilder.withType(DATETIME)
                        .withProp(SchemaProperty.STUDIO_TYPE, TalendType.DATE.getName());
                break;
            case java.sql.Types.BINARY:
            case java.sql.Types.VARBINARY:
            case java.sql.Types.LONGVARBINARY:
                entryBuilder.withType(BYTES).withProp(SchemaProperty.STUDIO_TYPE, TalendType.BYTES.getName());
                break;
            case java.sql.Types.BIGINT:
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
                entryBuilder.withType(DECIMAL).withProp(SchemaProperty.STUDIO_TYPE, TalendType.BIG_DECIMAL.getName());
                break;
            case java.sql.Types.CHAR:
                entryBuilder.withType(STRING).withProp(SchemaProperty.STUDIO_TYPE, TalendType.CHARACTER.getName());
                break;
            case java.sql.Types.VARCHAR:
            case java.sql.Types.LONGVARCHAR:
                entryBuilder.withType(STRING).withProp(SchemaProperty.STUDIO_TYPE, TalendType.STRING.getName());
            default:
                entryBuilder.withType(STRING).withProp(SchemaProperty.STUDIO_TYPE, TalendType.STRING.getName());
                break;
            }
        } else {
            // use talend db files if found
            MappingType<DbmsType, TalendType> mt = mapping.getDbmsMapping(columnTypeName);

            TalendType talendType;
            if (mt != null) {
                talendType = mt.getDefaultType();
                DbmsType sourceType = mt.getSourceType();

                isIgnoreLength = sourceType.isIgnoreLength();
                isIgnorePrecision = sourceType.isIgnorePrecision();
            } else {
                // if not find any mapping by current column db type name, map to studio string type
                talendType = TalendType.STRING;
            }

            entryBuilder.withProp(SchemaProperty.STUDIO_TYPE, talendType.getName());

            entryBuilder.withType(TalendTypeAndTckTypeConverter.convertTalendType2TckType(talendType));
        }

        if (columnTypeName != null && !columnTypeName.isEmpty()) {
            entryBuilder.withProp(SchemaProperty.ORIGIN_TYPE, columnTypeName);
        }

        // correct precision/scale/date pattern
        switch (dbtype) {
        case java.sql.Types.VARCHAR:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.INTEGER:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.DECIMAL:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        case java.sql.Types.BIGINT:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.NUMERIC:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        case java.sql.Types.TINYINT:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.DOUBLE:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        case java.sql.Types.FLOAT:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        case java.sql.Types.DATE:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            entryBuilder.withProp(SchemaProperty.PATTERN, "yyyy-MM-dd");
            break;
        case java.sql.Types.TIME:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            entryBuilder.withProp(SchemaProperty.PATTERN, "HH:mm:ss");
            break;
        case java.sql.Types.TIMESTAMP:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            entryBuilder.withProp(SchemaProperty.PATTERN, "yyyy-MM-dd HH:mm:ss.SSS");
            break;
        case java.sql.Types.BOOLEAN:
            break;
        case java.sql.Types.REAL:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        case java.sql.Types.SMALLINT:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.LONGVARCHAR:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        case java.sql.Types.CHAR:
            setPrecision(entryBuilder, isIgnoreLength, size);
            break;
        default:
            setPrecision(entryBuilder, isIgnoreLength, size);
            setScale(entryBuilder, isIgnorePrecision, scale);
            break;
        }

        return entryBuilder;
    }

    private static void setPrecision(Schema.Entry.Builder entryBuilder, boolean ignorePrecision, int precision) {
        if (ignorePrecision) {
            return;
        }

        entryBuilder.withProp(SchemaProperty.SIZE, String.valueOf(precision));
    }

    private static void setScale(Schema.Entry.Builder entryBuilder, boolean ignoreScale, int scale) {
        if (ignoreScale) {
            return;
        }

        entryBuilder.withProp(SchemaProperty.SCALE, String.valueOf(scale));
    }

    // this is for performance to avoid to do this computer when every row come
    public static List<TalendType> convertSchemaToTalendTypeList(Schema schema) {
        List<TalendType> result = new ArrayList<>();
        List<Schema.Entry> entries = schema.getEntries();
        for (int index = 0; index < entries.size(); index++) {
            Schema.Entry entry = entries.get(index);
            // though this works for studio schema fill, but also works for cloud as infer method will be executed
            // before this one
            String type = entry.getProp(SchemaProperty.STUDIO_TYPE);
            TalendType talendType;
            if (type != null) {
                talendType = TalendType.get(type);
            } else {
                talendType = TalendTypeAndTckTypeConverter.convertTckType2TalendType(entry.getType());
            }
            result.add(talendType);
        }
        return result;
    }

    public static void fillValue(final Record.Builder builder, final Schema schema,
            final List<TalendType> talendTypeList, final ResultSet resultSet,
            final boolean isTrimAll, Map<Integer, Boolean> trimMap)
            throws SQLException {
        List<Schema.Entry> entries = schema.getEntries();
        for (int index = 0; index < entries.size(); index++) {
            Schema.Entry entry = entries.get(index);

            int jdbcIndex = index + 1;

            // even cloud platform, should works well as we set it too
            TalendType talendType = talendTypeList.get(index);

            switch (talendType) {
            case STRING:
                String stingValue = resultSet.getString(jdbcIndex);
                Boolean isTrim = trimMap.get(jdbcIndex);
                if (stingValue != null) {
                    builder.withString(entry, (isTrimAll || (isTrim != null && isTrim)) ? stingValue.trim()
                            : stingValue);
                }
                break;
            case INTEGER:
                int intValue = resultSet.getInt(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withInt(entry, intValue);
                }
                break;
            case LONG:
                long longValue = resultSet.getLong(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withLong(entry, longValue);
                }
                break;
            case BOOLEAN:
                boolean booleanValue = resultSet.getBoolean(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withBoolean(entry, booleanValue);
                }
                break;
            case DATE:
                // this will lose precision, so use another withInstant method
                // builder.withTimestamp(entry, date.getTime());
                try {
                    Timestamp timestampValue = resultSet.getTimestamp(jdbcIndex);
                    if (timestampValue != null) {
                        builder.withInstant(entry, timestampValue.toInstant());
                    }
                } catch (Exception e) {
                    Date dateValue = resultSet.getDate(jdbcIndex);
                    if (dateValue != null) {
                        builder.withTimestamp(entry, dateValue.getTime());
                    }
                }
                break;
            case BIG_DECIMAL:
                BigDecimal decimalValue = resultSet.getBigDecimal(jdbcIndex);
                if (decimalValue != null) {
                    builder.withDecimal(entry, decimalValue);
                }
                break;
            case FLOAT:
                float floatValue = resultSet.getFloat(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withFloat(entry, floatValue);
                }
                break;
            case DOUBLE:
                double doubleValue = resultSet.getDouble(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withDouble(entry, doubleValue);
                }
                break;
            case BYTES:
                byte[] bytesValue = resultSet.getBytes(jdbcIndex);
                if (bytesValue != null) {
                    builder.withBytes(entry, bytesValue);
                }
                break;
            case SHORT:
                short shortValue = resultSet.getShort(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withInt(entry, shortValue);
                }
                break;
            case CHARACTER:
                String charValue = resultSet.getString(jdbcIndex);
                if (charValue != null) {
                    isTrim = trimMap.get(jdbcIndex);
                    builder.withString(entry, (isTrimAll || (isTrim != null && isTrim)) ? charValue.trim()
                            : charValue);
                }
                break;
            case BYTE:
                byte byteValue = resultSet.getByte(jdbcIndex);
                if (!resultSet.wasNull()) {
                    builder.withInt(entry, byteValue);
                }
                break;
            case OBJECT:
                Object objectValue = resultSet.getObject(jdbcIndex);
                if (objectValue != null) {
                    builder.with(entry, objectValue);
                }
                break;
            default:
                String value = resultSet.getString(jdbcIndex);
                isTrim = trimMap.get(jdbcIndex);
                builder.with(entry, (isTrimAll || (isTrim != null && isTrim)) ? value.trim()
                        : value);
                break;
            }
        }
    }

    public static Schema convertSchemaInfoList2TckSchema(List<SchemaInfo> infos,
            RecordBuilderFactory recordBuilderFactory) {
        final Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        convertBase(infos, recordBuilderFactory, schemaBuilder);
        return schemaBuilder.build();
    }

    public static Schema getRejectSchema(List<SchemaInfo> infos, RecordBuilderFactory recordBuilderFactory) {
        final Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);

        convertBase(infos, recordBuilderFactory, schemaBuilder);

        schemaBuilder.withEntry(recordBuilderFactory.newEntryBuilder()
                .withName("errorCode")
                .withType(STRING)
                .withProp(SchemaProperty.SIZE, "255")
                .build());
        schemaBuilder.withEntry(recordBuilderFactory.newEntryBuilder()
                .withName("errorMessage")
                .withType(STRING)
                .withProp(SchemaProperty.SIZE, "255")
                .build());

        return schemaBuilder.build();
    }

    private static void convertBase(List<SchemaInfo> infos, RecordBuilderFactory recordBuilderFactory,
            Schema.Builder schemaBuilder) {
        if (infos == null)
            return;

        infos.stream().forEach(info -> {
            Schema.Entry entry = convertSchemaInfo2Entry(info, recordBuilderFactory);
            if (entry != null) {
                schemaBuilder.withEntry(entry);
            }
        });
    }

    private static Schema.Entry convertSchemaInfo2Entry(SchemaInfo info, RecordBuilderFactory recordBuilderFactory) {
        if ("id_Dynamic".equals(info.getTalendType())) {
            return null;
        }

        Schema.Entry.Builder entryBuilder = recordBuilderFactory.newEntryBuilder();

        // TODO consider the valid name convert
        entryBuilder.withName(info.getLabel())
                .withRawName(info.getOriginalDbColumnName())
                .withNullable(info.isNullable())
                .withComment(info.getComment())
                .withDefaultValue(info.getDefaultValue())
                // in studio, we use talend type firstly, not tck type, but in cloud, no talend type, only tck type,
                // need to use this
                // but only studio have the design schema which with raw db type and talend type, so no need to
                // convert here as we will not use getType method
                .withType(TalendTypeAndTckTypeConverter.convertTalendType2TckType(TalendType.get(info.getTalendType())))
                // TODO also define a pro for origin db type like VARCHAR? info.getType()
                .withProp(SchemaProperty.STUDIO_TYPE, info.getTalendType())
                .withProp(SchemaProperty.IS_KEY, String.valueOf(info.isKey()));

        if (info.getPattern() != null) {
            entryBuilder.withProp(SchemaProperty.PATTERN, info.getPattern());
        }

        if (info.getLength() != null) {
            entryBuilder.withProp(SchemaProperty.SIZE, String.valueOf(info.getLength()));
        }

        if (info.getPrecision() != null) {
            entryBuilder.withProp(SchemaProperty.SCALE, String.valueOf(info.getPrecision()));
        }

        return entryBuilder.build();
    }

    public static Schema mergeRuntimeSchemaAndDesignSchema4Dynamic(List<SchemaInfo> designSchema, Schema runtimeSchema,
            RecordBuilderFactory recordBuilderFactory) {
        if (designSchema == null || designSchema.isEmpty()) {
            return runtimeSchema;
        }

        Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);

        List<Schema.Entry> runtimeFields = runtimeSchema.getEntries();

        Set<String> designFieldSet = new HashSet<>();
        for (SchemaInfo designField : designSchema) {
            if (!"id_Dynamic".equals(designField.getTalendType())) {
                String oname = designField.getOriginalDbColumnName();
                designFieldSet.add(oname);
            }
        }

        for (int i = 0; i < designSchema.size(); i++) {
            SchemaInfo designColumn = designSchema.get(i);

            if ("id_Dynamic".equals(designColumn.getTalendType())) {
                int dynamicSize = runtimeFields.size() - (designSchema.size() - 1);
                for (int j = 0; j < dynamicSize; j++) {
                    Schema.Entry runtimeEntry = runtimeFields.get(i + j);
                    String oname = runtimeEntry.getOriginalFieldName();
                    if (!designFieldSet.contains(oname)) {
                        schemaBuilder.withEntry(runtimeEntry);
                    }
                }
            } else {
                schemaBuilder.withEntry(convertSchemaInfo2Entry(designColumn, recordBuilderFactory));
            }
        }

        runtimeSchema.getProps().forEach((key, value) -> schemaBuilder.withProp(key, value));

        return schemaBuilder.build();
    }

    public static boolean containDynamic(List<SchemaInfo> designSchema) {
        for (SchemaInfo info : designSchema) {
            if ("id_Dynamic".equals(info.getTalendType())) {
                return true;
            }
        }
        return false;
    }

    public static int getDynamicIndex(List<SchemaInfo> designSchema) {
        int i = 0;
        for (SchemaInfo info : designSchema) {
            if ("id_Dynamic".equals(info.getTalendType())) {
                return i;
            }
            i++;
        }
        return -1;
    }

}
