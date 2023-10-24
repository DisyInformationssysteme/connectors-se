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

import java.util.Set;

// copy from daikon, why do this: as don't want to introduce daikon dependencies
// if we make sure no risk in future, will remove this class, use the same one in daikon
public class NameUtil {

    /**
     * correct the field name and make it valid for AVRO schema
     * for example :
     * input : "CA HT", output "CA_HT"
     * input : "column?!^Name", output "column___Name"
     * input : "P1_Vente_Qté", output "P1_Vente_Qt_"
     * 
     * @param name : the name will be correct
     * @param nameIndex : a index which is used to generate the column name when too much underline in the name
     * @param previousNames : the previous valid names, this is used to make sure that every name is different
     * @return the valid name, if the input name is null or empty, or the previousNames is null, return the input name
     * directly
     */
    public static String correct(String name, int nameIndex, Set<String> previousNames) {
        if (name == null || name.isEmpty() || previousNames == null) {
            return name;
        }

        StringBuilder str = new StringBuilder();
        int underLineCount = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) || ((c >= '0') && (c <= '9') && (i != 0))) {
                str.append(c);
            } else if (c == '_') {
                str.append(c);
                underLineCount++;
            } else {
                str.append('_');
                underLineCount++;
            }
        }

        String result = null;
        if (underLineCount > (name.length() / 2)) {
            result = "Column" + nameIndex;
        } else {
            result = str.toString();
        }

        return getUniqueName(result, previousNames);
    }

    private static String getUniqueName(String name, Set<String> previousNames) {
        boolean allIsDifferent = false;
        int index = 0;
        String currentName = name;
        while (!allIsDifferent) {
            allIsDifferent = true;

            if (previousNames.contains(currentName)) {
                allIsDifferent = false;
            }

            if (!allIsDifferent) {
                currentName = currentName + (++index);
            }
        }
        return currentName;
    }

}
