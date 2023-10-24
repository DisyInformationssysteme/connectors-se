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

import org.talend.sdk.component.api.record.Schema;

import static org.talend.sdk.component.api.record.Schema.Type.*;

public class TalendTypeAndTckTypeConverter {

    public static Schema.Type convertTalendType2TckType(TalendType talendType) {
        switch (talendType) {
        case STRING:
            return STRING;
        case BOOLEAN:
            return BOOLEAN;
        case INTEGER:
            return INT;
        case LONG:
            return LONG;
        case DOUBLE:
            return DOUBLE;
        case FLOAT:
            return FLOAT;
        case BYTE:
            // no Schema.Type.BYTE
            return INT;
        case BYTES:
            return BYTES;
        case SHORT:
            // no Schema.Type.SHORT
            return INT;
        case CHARACTER:
            // no Schema.Type.CHARACTER
            return STRING;
        case BIG_DECIMAL:
            return DECIMAL;
        case DATE:
            return DATETIME;
        case OBJECT:
            return STRING;
        default:
            throw new UnsupportedOperationException("Unrecognized type " + talendType);
        }
    }

    public static TalendType convertTckType2TalendType(Schema.Type tckType) {
        switch (tckType) {
        case STRING:
            return TalendType.STRING;
        case BOOLEAN:
            return TalendType.BOOLEAN;
        case INT:
            return TalendType.INTEGER;
        case LONG:
            return TalendType.LONG;
        case DOUBLE:
            return TalendType.DOUBLE;
        case FLOAT:
            return TalendType.FLOAT;
        case BYTES:
            return TalendType.BYTES;
        case DECIMAL:
            return TalendType.BIG_DECIMAL;
        case DATETIME:
            return TalendType.DATE;
        default:
            throw new UnsupportedOperationException("Unrecognized type " + tckType);
        }
    }

}
