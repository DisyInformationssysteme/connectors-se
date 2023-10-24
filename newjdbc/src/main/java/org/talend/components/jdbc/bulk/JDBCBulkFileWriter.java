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
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.schema.SchemaInferer;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate bulk file
 */
public class JDBCBulkFileWriter {

    protected JDBCBulkCommonConfig bulkCommonConfig;

    private CSVWriter csvWriter;

    private String charset = "UTF-8";

    private boolean isAppend;

    private String nullValue;

    private Schema designSchema;

    private Schema currentSchema;

    private boolean isDynamic;

    private BulkFormatter bulkFormatter;

    protected final RecordBuilderFactory recordBuilderFactory;

    private int totalCount;

    private transient List<SchemaInfo> schema;

    public JDBCBulkFileWriter(List<SchemaInfo> schema, JDBCBulkCommonConfig bulkCommonConfig, boolean isAppend,
            RecordBuilderFactory recordBuilderFactory) {
        this.bulkCommonConfig = bulkCommonConfig;
        this.recordBuilderFactory = recordBuilderFactory;

        this.isAppend = isAppend;
        if (bulkCommonConfig.isSetNullValue()) {
            this.nullValue = bulkCommonConfig.getNullValue();
        }

        this.schema = schema;

        this.designSchema = SchemaInferer.convertSchemaInfoList2TckSchema(schema, recordBuilderFactory);

        isDynamic = this.designSchema.getEntries().isEmpty() || SchemaInferer.containDynamic(schema);
    }

    public void open() throws IOException {
        String filepath = bulkCommonConfig.getBulkFile();
        if (filepath == null || filepath.isEmpty()) {
            throw new RuntimeException("Please set a valid value for \"Bulk File Path\" field.");
        }
        File file = new File(filepath);
        if (file.getParentFile().mkdirs()) {
            // fix findbug only
        }

        if (bulkCommonConfig.getRowSeparator().length() > 1) {
            throw new RuntimeException("only support one char row separator");
        }
        if (bulkCommonConfig.getFieldSeparator().length() > 1) {
            throw new RuntimeException("only support one char field separator");
        }
        csvWriter = new CSVWriter(new OutputStreamWriter(new java.io.FileOutputStream(file, isAppend), charset));
        csvWriter.setSeparator(bulkCommonConfig.getFieldSeparator().charAt(0));
        csvWriter.setLineEnd(bulkCommonConfig.getRowSeparator().substring(0, 1));

        if (bulkCommonConfig.isSetTextEnclosure()) {
            if (bulkCommonConfig.getTextEnclosure().length() > 1) {
                throw new RuntimeException("only support one char text enclosure");
            }
            // not let it to do the "smart" thing, avoid to promise too much for changing api in future
            csvWriter.setQuoteStatus(CSVWriter.QuoteStatus.FORCE);
            csvWriter.setQuoteChar(bulkCommonConfig.getTextEnclosure().charAt(0));
        } else {
            csvWriter.setQuoteStatus(CSVWriter.QuoteStatus.NO);
        }
        csvWriter.setEscapeChar('\\');

        fileIsEmpty = (file.length() == 0);
    }

    private boolean fileIsEmpty = false;

    public void write(Record input) throws IOException {
        if (null == input) {
            return;
        }

        if (currentSchema == null) {
            currentSchema = this.designSchema;
            Schema inputSchema = input.getSchema();

            if (isDynamic) {
                currentSchema = SchemaInferer.mergeRuntimeSchemaAndDesignSchema4Dynamic(schema, inputSchema,
                        recordBuilderFactory);
            }

            bulkFormatter =
                    new BulkFormatter(inputSchema, currentSchema, bulkCommonConfig.isSetTextEnclosure());
        }

        writeValues(input);

        totalCount++;
    }

    private void flush() throws IOException {
        csvWriter.flush();
    }

    public void close() throws IOException {
        flush();
        csvWriter.close();
    }

    private void writeValues(Record input) throws IOException {
        List<Schema.Entry> fields = currentSchema.getEntries();
        for (int i = 0; i < fields.size(); i++) {
            bulkFormatter.getFormatter(i).format(input, nullValue, csvWriter);
        }
        csvWriter.endRow();
    }
}
