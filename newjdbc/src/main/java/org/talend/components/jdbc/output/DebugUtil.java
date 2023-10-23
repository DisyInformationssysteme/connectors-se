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

public class DebugUtil {

    private String[] splits;

    private StringBuffer strBuffer;

    public DebugUtil(String sql) {
        sql += " ";
        splits = sql.split("\\?");
        strBuffer = new StringBuffer(32);
    }

    private int index = 0;

    public void writeHead() {
        if (index < splits.length) {
            strBuffer.append(splits[index++]);
        }
    }

    public void writeColumn(String columnContent, boolean textEnclose) {
        if (index < splits.length) {
            if (textEnclose) {
                strBuffer.append("'");
            }
            strBuffer.append(columnContent);
            if (textEnclose) {
                strBuffer.append("'");
            }
            strBuffer.append(splits[index++]);
        }
    }

    public String getSQL() {
        String sql = strBuffer.toString();
        index = 0;
        strBuffer = new StringBuffer(32);
        return sql;
    }

}
