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

import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.components.jdbc.schema.TalendType;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * construct the output tck record from input tck record and input schema and current component schema and also the
 * output
 * schema, we use this to avoid to do some same work for every input row
 *
 */
public class JDBCSPRecordCreator {

    private Schema outputSchema;

    private RecordBuilderFactory recordBuilderFactory;

    // the fields which need converter
    private Map<Integer, JDBCSPConverter> outputFieldLocation2AvroConverter = new HashMap<>();// more often

    // the field which store the whole result set object
    private int resultSetPostionOfOutputSchema = -1;// less often

    // the fields which propagate from input to output directly
    private Map<Integer, String> autoPropagatedFieldsFromInputToOutput = new HashMap<>();// less less often

    public void init(Schema currentComponentSchema, Schema outputSchema, JDBCSPConfig config,
            RecordBuilderFactory recordBuilderFactory) {
        // for tjdbcsp component, the output schema is the same with current component schema
        this.outputSchema = outputSchema;
        this.recordBuilderFactory = recordBuilderFactory;

        if (config.isFunction()) {
            Schema.Entry outField = CommonUtils.getField(currentComponentSchema, config.getResultColumn());
            int pos = CommonUtils.getFieldIndex(outputSchema, config.getResultColumn());
            outputFieldLocation2AvroConverter.put(pos, getSPConverter(outField, 1));
        }

        List<SPParameter> spParameters = config.getSpParameters();
        if (spParameters != null) {
            int i = config.isFunction() ? 2 : 1;
            int j = -1;
            for (SPParameter each : spParameters) {
                j++;
                String columnName = each.getColumnName();
                ParameterType pt = each.getParameterType();

                if (ParameterType.RECORDSET == pt) {
                    int pos = CommonUtils.getFieldIndex(outputSchema, columnName);
                    resultSetPostionOfOutputSchema = pos;
                    continue;
                }

                if (ParameterType.OUT == pt || ParameterType.INOUT == pt) {
                    Schema.Entry outField = CommonUtils.getField(currentComponentSchema, columnName);
                    int pos = CommonUtils.getFieldIndex(outputSchema, columnName);
                    outputFieldLocation2AvroConverter.put(pos,
                            getSPConverter(outField, i));
                }

                i++;
            }
        }
    }

    private boolean firstRowHaveCame = false;

    public Record createRecord(CallableStatement value, Record inputRecord) throws SQLException {
        if (!firstRowHaveCame) {
            firstRowHaveCame = true;

            Schema inputSchema = null;
            if (inputRecord != null) {
                inputSchema = inputRecord.getSchema();
            }

            Map<String, Schema.Entry> inputFieldMap = null;

            final List<Schema.Entry> entries = outputSchema.getEntries();
            for (int pos = 0; pos < entries.size(); pos++) {
                Schema.Entry outputField = entries.get(pos);
                if (outputFieldLocation2AvroConverter.containsKey(pos)
                        || (resultSetPostionOfOutputSchema == pos)) {
                    continue;
                }

                if (inputSchema == null) {
                    break;
                }

                List<Schema.Entry> inputFields = inputSchema.getEntries();

                if (inputFieldMap == null) {
                    inputFieldMap = new HashMap<>();
                    for (Schema.Entry inputField : inputFields) {
                        inputFieldMap.put(inputField.getName(), inputField);
                    }
                }

                Schema.Entry inputField = inputFieldMap.get(outputField.getName());
                if (inputField != null) {
                    autoPropagatedFieldsFromInputToOutput.put(pos, inputField.getName());
                }
            }
        }

        Record.Builder builder = recordBuilderFactory.newRecordBuilder(outputSchema);
        List<Schema.Entry> es = outputSchema.getEntries();
        for (int i = 0; i < es.size(); i++) {
            Schema.Entry entry = es.get(i);
            JDBCSPConverter converter = outputFieldLocation2AvroConverter.get(i);
            if (converter != null) {
                builder.with(entry, converter.convert(value));
                continue;
            }

            if (resultSetPostionOfOutputSchema == i) {
                builder.with(entry, value.getResultSet());
                continue;
            }

            String inputName = autoPropagatedFieldsFromInputToOutput.get(i);
            if (inputName != null && inputRecord != null) {
                builder.with(entry, inputRecord.get(Object.class, inputName));
            }
        }

        return builder.build();
    }

    private abstract class JDBCSPConverter {

        abstract Object convert(CallableStatement value) throws SQLException;

    }

    private JDBCSPConverter getSPConverter(Schema.Entry f, final int index) {
        Schema.Type type = f.getType();
        String talendType = f.getProp(SchemaProperty.STUDIO_TYPE);
        if (type == Schema.Type.STRING) {
            if (talendType == null || TalendType.STRING.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        String result = value.getString(index);
                        return result;
                    }
                };
            } else if (TalendType.BIG_DECIMAL.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        return value.getObject(index) == null ? null : value.getBigDecimal(index);
                    }
                };
            } else if (TalendType.SHORT.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        return value.getObject(index) == null ? null : value.getShort(index);
                    }
                };
            } else if (TalendType.CHARACTER.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        String result = value.getString(index);
                        return result != null && !result.isEmpty() ? result.charAt(0) : null;
                    }
                };
            } else if (TalendType.BYTE.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        return value.getObject(index) == null ? null : value.getByte(index);
                    }
                };
            }
        } else if (type == Schema.Type.INT) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getInt(index);
                }
            };
        } else if (type == Schema.Type.DATETIME) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    try {
                        return value.getObject(index) == null ? null : value.getTimestamp(index).toInstant();
                    } catch (Exception e) {
                        return value.getDate(index).getTime();
                    }
                }
            };
        } else if (type == Schema.Type.LONG) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getLong(index);
                }
            };
        } else if (type == Schema.Type.DOUBLE) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getDouble(index);
                }
            };
        } else if (type == Schema.Type.FLOAT) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getFloat(index);
                }
            };
        } else if (type == Schema.Type.BOOLEAN) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getBoolean(index);
                }
            };
        } else if (type == Schema.Type.DECIMAL) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    return value.getObject(index) == null ? null : value.getBigDecimal(index);
                }
            };
        } else if (type == Schema.Type.BYTES) {
            return new JDBCSPConverter() {

                public Object convert(CallableStatement value) throws SQLException {
                    Object result = value.getBytes(index);
                    return value.wasNull() ? null : result;
                }
            };
        } else {
            if (TalendType.OBJECT.getName().equals(talendType)) {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        return value.getObject(index);
                    }
                };
            } else {
                return new JDBCSPConverter() {

                    public Object convert(CallableStatement value) throws SQLException {
                        String result = value.getString(index);
                        return result;
                    }
                };
            }
        }

        throw new RuntimeException("can't find converter for current type:" + type);
    }

}
