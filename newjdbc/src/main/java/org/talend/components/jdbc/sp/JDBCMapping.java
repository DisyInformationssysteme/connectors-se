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

import org.talend.components.jdbc.schema.TalendType;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * the mapping tool for JDBC
 * this class only work for tJDBCSP component now
 */
public class JDBCMapping {

    /**
     * fill the prepared statement object
     * 
     * @param index
     * @param statement
     * @param f
     * @param value
     * @throws SQLException
     */
    public static void setValue(final int index, final PreparedStatement statement, final Schema.Entry f,
            final Object value)
            throws SQLException {
        final Schema.Type type = f.getType();
        final String studioType = f.getProp(SchemaProperty.STUDIO_TYPE);

        if (value == null) {
            if (type == Schema.Type.STRING) {
                int sqlType = Types.VARCHAR;
                if (studioType != null) {
                    if (TalendType.CHARACTER.getName().equals(studioType)) {
                        sqlType = Types.CHAR;
                    }
                }
                statement.setNull(index, sqlType);
            } else if (type == Schema.Type.INT) {
                int sqlType = Types.INTEGER;
                if (studioType != null) {
                    if (TalendType.SHORT.getName().equals(studioType)) {
                        sqlType = Types.SMALLINT;
                    } else if (TalendType.BYTE.getName().equals(studioType)) {
                        sqlType = Types.TINYINT;
                    }
                }
                statement.setNull(index, sqlType);
            } else if (type == Schema.Type.DATETIME) {
                statement.setNull(index, Types.TIMESTAMP);
            } else if (type == Schema.Type.LONG) {
                statement.setNull(index, Types.BIGINT);
            } else if (type == Schema.Type.DOUBLE) {
                statement.setNull(index, Types.DOUBLE);
            } else if (type == Schema.Type.FLOAT) {
                statement.setNull(index, Types.FLOAT);
            } else if (type == Schema.Type.BOOLEAN) {
                statement.setNull(index, Types.BOOLEAN);
            } else if (type == Schema.Type.DECIMAL) {
                statement.setNull(index, Types.DECIMAL);
            } else if (type == Schema.Type.BYTES) {
                // TODO check it, now only make a progress and make sure no regression with current version
                // ARRAY is not common, don't exist on lots of database,
                // here only use the old way in javajet component for tjdbcoutput, not sure it works, maybe change it to
                // BLOB
                statement.setNull(index, Types.ARRAY);
            } else {
                statement.setNull(index, Types.JAVA_OBJECT);
            }

            return;
        }

        if (type == Schema.Type.STRING) {
            statement.setString(index, String.valueOf(value));
        } else if (type == Schema.Type.INT) {
            if (studioType != null && TalendType.SHORT.getName().equals(studioType)) {
                statement.setShort(index, (Short) value);
            } else if (studioType != null && TalendType.BYTE.getName().equals(studioType)) {
                statement.setByte(index, (Byte) value);
            } else {
                statement.setInt(index, (Integer) value);
            }
        } else if (type == Schema.Type.DATETIME) {
            if (value instanceof Long) {
                statement.setTimestamp(index, new java.sql.Timestamp(Long.class.cast(value)));
            } else {
                java.util.Date date = (java.util.Date) value;
                statement.setTimestamp(index, new java.sql.Timestamp(date.getTime()));
            }
        } else if (type == Schema.Type.LONG) {
            statement.setLong(index, (Long) value);
        } else if (type == Schema.Type.DOUBLE) {
            statement.setDouble(index, (Double) value);
        } else if (type == Schema.Type.FLOAT) {
            statement.setFloat(index, (Float) value);
        } else if (type == Schema.Type.BOOLEAN) {
            statement.setBoolean(index, (Boolean) value);
        } else if (type == Schema.Type.DECIMAL) {
            statement.setBigDecimal(index, (BigDecimal) value);
        } else if (type == Schema.Type.BYTES) {
            // TODO check it, now only make a progress and make sure no regression with current version
            statement.setBytes(index, (byte[]) value);
        } else {
            statement.setObject(index, value);
        }
    }

    /**
     * work for tJDBCSP components
     * 
     * @param f
     * @return
     */
    public static int getSQLTypeFromTckType(Schema.Entry f) {
        final Schema.Type type = f.getType();
        final String studioType = f.getProp(SchemaProperty.STUDIO_TYPE);

        if (type == Schema.Type.STRING) {
            if (studioType != null) {
                if (TalendType.CHARACTER.getName().equals(studioType)) {
                    return Types.CHAR;
                }
            }
            return Types.VARCHAR;
        } else if (type == Schema.Type.INT) {
            if (studioType != null) {
                if (TalendType.SHORT.getName().equals(studioType)) {
                    return Types.SMALLINT;
                } else if (TalendType.BYTE.getName().equals(studioType)) {
                    return Types.TINYINT;
                }
            }
            return Types.INTEGER;
        } else if (type == Schema.Type.DATETIME) {
            return Types.DATE;
        } else if (type == Schema.Type.LONG) {
            return Types.BIGINT;
        } else if (type == Schema.Type.DOUBLE) {
            return Types.DOUBLE;
        } else if (type == Schema.Type.FLOAT) {
            return Types.FLOAT;
        } else if (type == Schema.Type.BOOLEAN) {
            return Types.BOOLEAN;
        } else if (type == Schema.Type.DECIMAL) {
            return Types.DECIMAL;
        } else if (type == Schema.Type.BYTES) {
            // TODO check it, now only make a progress and make sure no regression with current version
            // TODO maybe make it to ARRAY or BLOB?
            return Types.OTHER;
        } else {
            return Types.OTHER;
        }
    }
}
