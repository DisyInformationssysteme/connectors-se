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
package org.talend.components.jdbc.bulk;

import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FormatterUtils {

    public static final String dateDefaultPattern = "dd-MM-yyyy";

    public static String formatDate(Date date, String pattern) {
        if (date != null) {
            DateFormat format = FastDateParser.getInstance(pattern == null ? dateDefaultPattern : pattern);
            format.setTimeZone(TimeZone.getDefault());
            return format.format(date);
        } else {
            return null;
        }
    }

    /**
     * in order to transform the number "1234567.89" to string 123,456.89
     */
    public static String formatNumber(String s, Character thousandsSeparator, Character decimalSeparator) {
        if (s == null) {
            return null;
        }
        String result = s;
        int decimalIndex = s.indexOf("."); //$NON-NLS-1$

        if (decimalIndex == -1) {
            if (thousandsSeparator != null) {
                return formatNumber(result, thousandsSeparator);
            } else {
                return result;
            }
        }

        if (thousandsSeparator != null) {
            result = formatNumber(s.substring(0, decimalIndex), thousandsSeparator);
        } else {
            result = s.substring(0, decimalIndex);
        }

        if (decimalSeparator != null) {
            result += (s.substring(decimalIndex)).replace('.', decimalSeparator);
        } else {
            result += s.substring(decimalIndex);
        }
        return result;
    }

    private static String formatNumber(String s, char thousandsSeparator) {

        StringBuilder sb = new StringBuilder(s);
        int index = sb.length();

        index = index - 3;
        while (index > 0 && sb.charAt(index - 1) != '-') {
            sb.insert(index, thousandsSeparator);
            index = index - 3;
        }

        return sb.toString();
    }

}
