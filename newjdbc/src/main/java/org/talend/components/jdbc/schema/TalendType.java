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

import java.util.HashMap;
import java.util.Map;

/**
 * Talend data type
 */
public enum TalendType {

    LIST("id_List"),
    BOOLEAN("id_Boolean"),
    BYTE("id_Byte"),
    BYTES("id_byte[]"),
    CHARACTER("id_Character"),
    DATE("id_Date"),
    BIG_DECIMAL("id_BigDecimal"),
    DOUBLE("id_Double"),
    FLOAT("id_Float"),
    INTEGER("id_Integer"),
    LONG("id_Long"),
    OBJECT("id_Object"),
    SHORT("id_Short"),
    STRING("id_String");

    private static final Map<String, TalendType> talendTypes = new HashMap<>();

    static {
        for (TalendType talendType : values()) {
            talendTypes.put(talendType.typeName, talendType);
        }
    }

    private final String typeName;

    private TalendType(String typeName) {
        this.typeName = typeName;
    }

    public String getName() {
        return typeName;
    }

    /**
     * Provides {@link TalendType} by its name
     * 
     * @param typeName {@link TalendType} name
     * @return {@link TalendType}
     */
    public static TalendType get(String typeName) {
        TalendType talendType = talendTypes.get(typeName);
        if (talendType == null) {
            throw new IllegalArgumentException(
                    String.format("Invalid value %s, it should be one of %s", typeName, talendTypes));
        }
        return talendType;
    }

}
