/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
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
package org.talend.components.rejector.service;

import org.talend.components.rejector.component.sink.RejectorProcessorConfiguration;
import org.talend.components.rejector.configuration.RejectorDataSet;
import org.talend.components.rejector.configuration.RejectorDataStore;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Type;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus.Status;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.api.service.schema.DiscoverSchema;
import org.talend.sdk.component.api.service.schema.DiscoverSchemaExtended;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UiServices {

    @Service
    private RecordBuilderFactory factory;

    @HealthCheck("healthCheck")
    public HealthCheckStatus healthCheck(@Option("configuration") final RejectorDataStore datastore) {
        log.warn("[healthCheck] {}", datastore);
        return new HealthCheckStatus(Status.OK, "Connection OK");
    }

    @DiscoverSchemaExtended("RejectorProcessorSchema")
    public Schema discoverProcessorSchema(final Schema incomingSchema,
            @Option final RejectorProcessorConfiguration configuration, final String branch) {
        log.warn("[discoverProcessorSchema]branch: {}, incoming: {}, conf: {}.", branch, incomingSchema, configuration);
        final Schema.Builder outgoingSchema = factory.newSchemaBuilder(incomingSchema);
        final String code = configuration.getCode();
        final String infos = configuration.getDataSet().getInformations();
        final String url = configuration.getDataSet().getDataStore().getUrl();
        outgoingSchema.withEntry(factory.newEntryBuilder()
                .withName("RejectorProcessorSchema")
                .withType(Type.STRING)
                .withComment(infos)
                .withProp("talend.studio.key", "true")
                .build());
        outgoingSchema.withEntry(factory.newEntryBuilder()
                .withName(branch)
                .withType(Type.FLOAT)
                .withComment(infos)
                .withProp("talend.studio.length", "10")
                .withProp("talend.studio.precision", "3")
                .build());
        if ("REJECT".equals(branch.toUpperCase())) {
            outgoingSchema.withEntry(factory.newEntryBuilder()
                    .withName("ERROR_MESSAGE")
                    .withType(Type.STRING)
                    .withComment(url)
                    .withDefaultValue(code)
                    .build());
        }
        return outgoingSchema.build();
    }

    @DiscoverSchemaExtended("RejectorDataSet")
    public Schema discoverInputSchema(final Schema incomingSchema,
            @Option final RejectorProcessorConfiguration configuration, final String branch) {
        log.warn("[discoverProcessorSchema]branch: {}, incoming: {}, conf: {}.", branch, incomingSchema, configuration);
        final Schema.Builder outgoingSchema = factory.newSchemaBuilder(incomingSchema);
        final String code = configuration.getCode();
        final String infos = configuration.getDataSet().getInformations();
        final String url = configuration.getDataSet().getDataStore().getUrl();
        outgoingSchema.withEntry(factory.newEntryBuilder()
                .withName("RejectorDataSet")
                .withType(Type.STRING)
                .withComment(infos)
                .withProp("talend.studio.key", "true")
                .build());
        outgoingSchema.withEntry(factory.newEntryBuilder()
                .withName(branch)
                .withType(Type.FLOAT)
                .withComment(infos)
                .withProp("talend.studio.length", "10")
                .withProp("talend.studio.precision", "3")
                .build());
        if ("REJECT".equals(branch.toUpperCase())) {
            outgoingSchema.withEntry(factory.newEntryBuilder()
                    .withName("ERROR_MESSAGE")
                    .withType(Type.STRING)
                    .withComment(url)
                    .withDefaultValue(code)
                    .build());
        }
        return outgoingSchema.build();
    }

    @DiscoverSchema("RejectorDataSetor")
    public Schema guessSchema(@Option final RejectorDataSet dataset) {
        Schema schema = factory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(factory.newEntryBuilder()
                        .withName("RejectorDataSetor")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .withComment("comment - " + dataset.getDataStore().getUrl())
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("dataset_" + dataset.getInformations())
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .withComment("comment - " + dataset.getDataStore().getUrl())
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("nullStr")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .withRawName("A long string")
                        .withDefaultValue("sdsddsd")
                        .withComment("comment - nullstr")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("inty")
                        .withType(Schema.Type.INT)
                        .withNullable(true)
                        .withDefaultValue(101)
                        .withRawName("Along_int")
                        .withComment("comment - inty")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("floaty")
                        .withType(Schema.Type.FLOAT)
                        .withNullable(true)
                        .withDefaultValue(202.2f)
                        .withRawName("A long floatydouble")
                        .withComment("comment - floatydoubvlyu")
                        .withProp("length", "5")
                        .withProp("precision", "2")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("doubly")
                        .withType(Schema.Type.DOUBLE)
                        .withNullable(true)
                        .withDefaultValue(202.2)
                        .withRawName("A long double")
                        .withComment("comment - doubvlyu")
                        .withProp("length", "10")
                        .withProp("precision", "3")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("booly")
                        .withType(Schema.Type.BOOLEAN)
                        .withNullable(true)
                        .withDefaultValue(Boolean.TRUE)
                        .withRawName("A long bool")
                        .withComment("comment - bool")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("daty")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(false)
                        .withRawName("A long date")
                        .withProp("pattern", "yyyy-MM-dd HH:mm")
                        .withComment("comment - daty")
                        .build())
                .withEntry(factory.newEntryBuilder()
                        .withName("daty2")
                        .withType(Schema.Type.DATETIME)
                        .withNullable(true)
                        .withRawName("A long date2")
                        .withComment("comment - daty2")
                        .build())
                .build();
        // log.warn("[guessSchema] returning {}", schema);
        return schema;
    }

}
