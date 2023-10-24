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

import com.talend.csv.CSVWriter;
import org.talend.components.jdbc.schema.CommonUtils;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BulkFormatter {

    private Formatter[] formatter;

    private boolean useTextEnclosure;

    public BulkFormatter(Schema inputSchema, Schema currentSchema, boolean useTextEnclosure) {
        List<Formatter> writers = new ArrayList<Formatter>();
        List<Schema.Entry> fields = currentSchema.getEntries();

        for (Schema.Entry field : fields) {
            Schema.Entry inputField = CommonUtils.getField(inputSchema, field.getName());

            Schema.Entry componentField = CommonUtils.getField(currentSchema, field.getName());
            String inputValueName = inputField.getName();
            String pattern = componentField.getProp(SchemaProperty.PATTERN);

            Schema.Type type = componentField.getType();

            Formatter writer = null;

            if (type == Schema.Type.DATETIME) {
                writer = new DateTypeWriter(inputValueName, pattern);
            } else if (type == Schema.Type.BYTES) {
                writer = new BytesTypeWriter(inputValueName);
            } else {
                writer = new StringTypeWriter(inputValueName);
            }

            writers.add(writer);
        }

        formatter = writers.toArray(new Formatter[0]);
        this.useTextEnclosure = useTextEnclosure;
    }

    public Formatter getFormatter(int i) {
        return formatter[i];
    }

    public class Formatter {

        protected final String inputValueName;

        Formatter(String inputValueName) {
            this.inputValueName = inputValueName;
        }

        public void format(Record input, String nullValue, CSVWriter writer) {
            // do nothing
        }

    }

    private void fillNull(String nullValue, CSVWriter writer) {
        writer.setQuoteStatus(CSVWriter.QuoteStatus.NO);
        if (nullValue != null) {
            writer.writeColumn(nullValue);
        } else {
            writer.writeColumn("");
        }
        if (useTextEnclosure) {
            writer.setQuoteStatus(CSVWriter.QuoteStatus.FORCE);
        } else {
            writer.setQuoteStatus(CSVWriter.QuoteStatus.NO);
        }
    }

    public class StringTypeWriter extends Formatter {

        StringTypeWriter(String inputValueLocation) {
            super(inputValueLocation);
        }

        public void format(Record input, String nullValue, CSVWriter writer) {
            Object inputValue = input.get(Object.class, inputValueName);
            if (inputValue == null) {
                fillNull(nullValue, writer);
            } else {
                writer.writeColumn(String.valueOf(inputValue));
            }
        }
    }

    public class DateTypeWriter extends Formatter {

        private String pattern;

        DateTypeWriter(String inputValueName, String pattern) {
            super(inputValueName);
            this.pattern = pattern;
        }

        public void format(Record input, String nullValue, CSVWriter writer) {
            Object inputValue = input.get(Object.class, inputValueName);
            if (inputValue == null) {
                fillNull(nullValue, writer);
            } else {
                if (inputValue instanceof Long) {
                    writer.writeColumn(FormatterUtils.formatDate(new Date(Long.class.cast(inputValue)), pattern));
                } else {
                    writer.writeColumn(FormatterUtils.formatDate((Date) inputValue, pattern));
                }
            }
        }

    }

    class BytesTypeWriter extends Formatter {

        // always use utf8? bytes array can't mean a lob object?
        // now use utf8, not use platform default as easy migration if fix it in future
        private Charset charset = Charset.forName("UTF-8");

        BytesTypeWriter(String inputValueLocation) {
            super(inputValueLocation);
        }

        public void format(Record input, String nullValue, CSVWriter writer) {
            Object inputValue = input.get(Object.class, inputValueName);
            if (inputValue == null) {
                fillNull(nullValue, writer);
            } else {
                writer.writeColumn(charset.decode(ByteBuffer.wrap((byte[]) inputValue)).toString());
            }
        }

    }

}
