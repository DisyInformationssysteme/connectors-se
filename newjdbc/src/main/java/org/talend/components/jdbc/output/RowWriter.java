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
package org.talend.components.jdbc.output;

import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.TalendType;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RowWriter {

    private TypeWriter[] typeWriters;

    private final boolean debug;

    private DebugUtil debugUtil;

    public RowWriter(List<JDBCSQLBuilder.Column> columnList, Schema inputSchema, Schema currentSchema,
            PreparedStatement statement) {
        this(columnList, inputSchema, currentSchema, statement, false, null);
    }

    public RowWriter(List<JDBCSQLBuilder.Column> columnList, Schema inputSchema, Schema currentSchema,
            PreparedStatement statement, boolean debug, String sql) {
        this.debug = debug;

        if (debug) {
            debugUtil = new DebugUtil(sql);
        }

        List<TypeWriter> writers = new ArrayList<TypeWriter>();

        int statementIndex = 0;

        for (JDBCSQLBuilder.Column column : columnList) {
            Schema.Entry inputField = CommonUtils.getField(inputSchema, column.columnLabel);

            Schema.Entry componentField = CommonUtils.getField(currentSchema, column.columnLabel);
            final String inputValueName = inputField.getName();
            String pattern = componentField.getProp(SchemaProperty.PATTERN);
            statementIndex++;

            Schema.Type type = componentField.getType();
            String talendType = componentField.getProp(SchemaProperty.STUDIO_TYPE);

            TypeWriter writer = null;

            if (type == Schema.Type.STRING) {
                if (TalendType.CHARACTER.getName().equals(talendType)) {
                    writer = new CharacterTypeWriter(statement, statementIndex, inputValueName);
                } else if (TalendType.OBJECT.getName().equals(talendType)) {
                    writer = new ObjectTypeWriter(statement, statementIndex, inputValueName);
                } else {
                    writer = new StringTypeWriter(statement, statementIndex, inputValueName);
                }
            } else if (type == Schema.Type.INT) {
                if (TalendType.BYTE.getName().equals(talendType)) {
                    writer = new ByteTypeWriter(statement, statementIndex, inputValueName);
                } else if (TalendType.SHORT.getName().equals(talendType)) {
                    writer = new ShortTypeWriter(statement, statementIndex, inputValueName);
                } else {
                    writer = new IntTypeWriter(statement, statementIndex, inputValueName);
                }
            } else if (type == Schema.Type.DATETIME) {
                writer = new DateTypeWriter(statement, statementIndex, inputValueName, pattern);
            } else if (type == Schema.Type.LONG) {
                writer = new LongTypeWriter(statement, statementIndex, inputValueName);
            } else if (type == Schema.Type.DOUBLE) {
                writer = new DoubleTypeWriter(statement, statementIndex, inputValueName);
            } else if (type == Schema.Type.FLOAT) {
                writer = new FloatTypeWriter(statement, statementIndex, inputValueName);
            } else if (type == Schema.Type.BOOLEAN) {
                writer = new BooleanTypeWriter(statement, statementIndex, inputValueName);
            } else if (type == Schema.Type.DECIMAL) {
                writer = new BigDecimalTypeWriter(statement, statementIndex, inputValueName);
            } else if (type == Schema.Type.BYTES) {
                writer = new BytesTypeWriter(statement, statementIndex, inputValueName);
            } else {
                writer = new ObjectTypeWriter(statement, statementIndex, inputValueName);
            }

            writers.add(writer);
        }

        typeWriters = writers.toArray(new TypeWriter[0]);
    }

    public String write(Record input) throws SQLException {
        if (debug) {
            debugUtil.writeHead();
        }

        for (TypeWriter writer : typeWriters) {
            writer.write(input);
        }

        if (debug) {
            return debugUtil.getSQL();
        }

        return null;
    }

    private void writeDebugColumnNullContent() {
        if (debug) {
            debugUtil.writeColumn(null, false);
        }
    }

    class TypeWriter {

        protected final PreparedStatement statement;

        protected final int statementIndex;

        protected final String inputValueName;

        protected TypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            this.statement = statement;
            this.statementIndex = statementIndex;
            this.inputValueName = inputValueName;
        }

        void write(Record input) throws SQLException {
            // do nothing
        }

    }

    class StringTypeWriter extends TypeWriter {

        StringTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            String inputValue = input.getString(inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.VARCHAR);
                writeDebugColumnNullContent();
            } else {
                statement.setString(statementIndex, inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue, true);
                }
            }
        }
    }

    class IntTypeWriter extends TypeWriter {

        IntTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Integer inputValue = input.get(Integer.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.INTEGER);
                writeDebugColumnNullContent();
            } else {
                statement.setInt(statementIndex, (int) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class DateTypeWriter extends TypeWriter {

        private String pattern;

        DateTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName, String pattern) {
            super(statement, statementIndex, inputValueName);
            this.pattern = pattern;
        }

        public void write(Record input) throws SQLException {
            Instant inputValue = input.getInstant(inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.TIMESTAMP);
            } else {
                // some jdbc implement may not follow jdbc spec, for example :
                // mysq_preparestatement.setTimestamp(oracle_timestamp) may not work
                // but that is only a guess, may not right, maybe can set directly, so do the thing below
                // here only for safe, so do like this
                Timestamp timestamp = Timestamp.from(inputValue);
                statement.setTimestamp(statementIndex, timestamp);
                debug(timestamp);
            }
        }

        private void debug(Object inputValue) {
            if (debug) {
                if (pattern == null || pattern.length() == 0) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                    debugUtil.writeColumn(sdf.format((Date) inputValue), false);
                }
            }
        }

    }

    class BigDecimalTypeWriter extends TypeWriter {

        BigDecimalTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            BigDecimal inputValue = input.get(BigDecimal.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.DECIMAL);
                writeDebugColumnNullContent();
            } else {
                statement.setBigDecimal(statementIndex, inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class LongTypeWriter extends TypeWriter {

        LongTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Long inputValue = input.get(Long.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.INTEGER);
                writeDebugColumnNullContent();
            } else {
                statement.setLong(statementIndex, (long) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class DoubleTypeWriter extends TypeWriter {

        DoubleTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Double inputValue = input.get(Double.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.DOUBLE);
                writeDebugColumnNullContent();
            } else {
                statement.setDouble(statementIndex, (double) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class FloatTypeWriter extends TypeWriter {

        FloatTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Float inputValue = input.get(Float.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.FLOAT);
                writeDebugColumnNullContent();
            } else {
                statement.setFloat(statementIndex, (float) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class BooleanTypeWriter extends TypeWriter {

        BooleanTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Boolean inputValue = input.get(Boolean.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.BOOLEAN);
                writeDebugColumnNullContent();
            } else {
                statement.setBoolean(statementIndex, (boolean) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class ShortTypeWriter extends TypeWriter {

        ShortTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Short inputValue = input.get(Short.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.INTEGER);
                writeDebugColumnNullContent();
            } else {
                statement.setShort(statementIndex, ((Number) inputValue).shortValue());
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class ByteTypeWriter extends TypeWriter {

        ByteTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Object inputValue = input.get(Byte.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.INTEGER);
                writeDebugColumnNullContent();
            } else {
                // please see org.talend.codegen.enforcer.IncomingSchemaEnforcer, it will convert byte(Byte) to
                // int(Integer), not
                // know why, so change here
                // statement.setByte(statementIndex, (byte) inputValue);
                statement.setByte(statementIndex, ((Number) inputValue).byteValue());
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class CharacterTypeWriter extends TypeWriter {

        CharacterTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Character inputValue = input.get(Character.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.CHAR);
                writeDebugColumnNullContent();
            } else {
                statement.setString(statementIndex, String.valueOf(inputValue));
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), true);
                }
            }
        }

    }

    class BytesTypeWriter extends TypeWriter {

        BytesTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Object inputValue = input.get(Object.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.ARRAY);
                writeDebugColumnNullContent();
            } else {
                statement.setBytes(statementIndex, (byte[]) inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

    class ObjectTypeWriter extends TypeWriter {

        ObjectTypeWriter(PreparedStatement statement, int statementIndex, String inputValueName) {
            super(statement, statementIndex, inputValueName);
        }

        public void write(Record input) throws SQLException {
            Object inputValue = input.get(Object.class, inputValueName);
            if (inputValue == null) {
                statement.setNull(statementIndex, java.sql.Types.JAVA_OBJECT);
                writeDebugColumnNullContent();
            } else {
                statement.setObject(statementIndex, inputValue);
                if (debug) {
                    debugUtil.writeColumn(inputValue.toString(), false);
                }
            }
        }

    }

}
