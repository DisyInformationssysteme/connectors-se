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
package org.talend.components.http.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.common.httpclient.api.HTTPMethod;
import org.talend.components.common.httpclient.api.authentication.OAuth20;
import org.talend.components.http.configuration.Dataset;
import org.talend.components.http.configuration.Format;
import org.talend.components.http.configuration.OutputContent;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.localconf.HTTPLocalConfig;
import org.talend.components.http.configuration.pagination.Pagination;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.exception.ComponentException;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.completion.Suggestions;
import org.talend.sdk.component.api.service.configuration.Configuration;
import org.talend.sdk.component.api.service.schema.DiscoverSchema;
import org.talend.sdk.component.api.service.schema.DiscoverSchemaExtended;
import org.talend.sdk.component.api.service.update.Update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UIService {

    public final static String ACTION_HTTP_METHOD_LIST = "ACTION_HTTP_METHOD_LIST";

    public final static String ACTION_DISCOVER_SCHEMA_EXTENDED = "ACTION_DISCOVER_SCHEMA_EXTENDED";

    public final static String ACTION_DISCOVER_SCHEMA = "Dataset";

    public final static String ACTION_PAGINATION_FLAVOR_LIST = "ACTION_PAGINATION_FLAVOR_LIST";

    public final static String ACTION_UPDATE_PAGINATION_PRESET = "ACTION_UPDATE_PAGINATION_PRESET";

    public final static String ACTION_SUGGESTABLE_LOAD_OAUTH2_FORM_PARAMS =
            "ACTION_SUGGESTABLE_LOAD_OAUTH2_FORM_PARAMS";

    @Service
    @Getter
    private RecordBuilderService recordBuilderService;

    @Configuration("http-common")
    private Supplier<HTTPLocalConfig> httpLocalConfig;

    @Service
    private I18n i18n;

    @Suggestions(ACTION_HTTP_METHOD_LIST)
    public SuggestionValues getHTTPMethodList() {
        List<SuggestionValues.Item> items = Arrays.stream(HTTPMethod.values())
                .map(e -> new SuggestionValues.Item(e.name(), e.name()))
                .collect(Collectors.toList());
        return new SuggestionValues(true, items);
    }

    @Suggestions(ACTION_PAGINATION_FLAVOR_LIST)
    public SuggestionValues getPaginationFlavorList() {
        List<SuggestionValues.Item> flavors = httpLocalConfig.get()
                .getPaginations()
                .stream()
                .map(p -> new SuggestionValues.Item(p.getPreset(), p.getPreset()))
                .collect(Collectors.toList());
        return new SuggestionValues(true, flavors);
    }

    @Update(ACTION_UPDATE_PAGINATION_PRESET)
    public Pagination autoconfigurationPaginationByFlavor(final Pagination inputPagination) {
        Optional<Pagination> selection = httpLocalConfig.get()
                .getPaginations()
                .stream()
                .filter(p -> p.getPreset().equals(inputPagination.getPreset()))
                .findFirst();

        if (selection.isPresent()) {
            return selection.get();
        }

        return inputPagination;
    }

    /**
     * TODO: remove incomingSchema and branch paramters once https://jira.talendforge.org/browse/TCOMP-2311 is done
     *
     * @param incomingSchema
     * @param config
     * @param branch
     * @return
     */
    @DiscoverSchemaExtended(ACTION_DISCOVER_SCHEMA_EXTENDED)
    public Schema discoverSchemaExtended(final Schema incomingSchema,
            @Option("configuration") final RequestConfig config, String branch) {
        return _discoverSchema(config, incomingSchema);
    }

    @DiscoverSchema(ACTION_DISCOVER_SCHEMA)
    public Schema discoverSchema(@Option("configuration") final Dataset config) {
        RequestConfig rc = new RequestConfig();
        rc.setDataset(config);
        return _discoverSchema(rc, null);
    }

    private Schema _discoverSchema(final RequestConfig config, final Schema incomingSchema) {
        List<Schema.Entry> entries = new ArrayList<>();

        if (config.getDataset().isOutputKeyValuePairs()) {
            if (config.getDataset().isForwardInput() && incomingSchema != null) {
                // Forward incoming schema first, if configured
                entries.addAll(incomingSchema.getEntries());
            }
            // If output key/value pairs, schema is those keys
            config.getDataset().getKeyValuePairs().stream().forEach(x -> {
                Schema.Entry e = recordBuilderService.getRecordBuilderFactory()
                        .newEntryBuilder()
                        .withName(x.getKey())
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build();
                entries.add(e);
            });
        } else if (config.getDataset().getFormat() == Format.JSON
                && config.getDataset().getReturnedContent() == OutputContent.BODY_ONLY) {
            // It can't infer schema without executing the HTTP query, but it is forbidden.
            throw new ComponentException(i18n.notAllowedToExecCallForDiscoverSchema());
        } else {
            // In other case there is a 'body' entry
            entries.add(recordBuilderService.getRecordBuilderFactory()
                    .newEntryBuilder()
                    .withName("body")
                    .withType(Schema.Type.STRING)
                    .withNullable(true)
                    .build());

            if (config.getDataset().getReturnedContent() == OutputContent.STATUS_HEADERS_BODY) {
                // and 'status' & 'header' if needed
                entries.add(recordBuilderService.getRecordBuilderFactory()
                        .newEntryBuilder()
                        .withName("headers")
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .build());
                entries.add(recordBuilderService.getRecordBuilderFactory()
                        .newEntryBuilder()
                        .withName("status")
                        .withType(Schema.Type.INT)
                        .withNullable(false)
                        .build());
            }
        }

        Schema.Builder builder = recordBuilderService.getRecordBuilderFactory().newSchemaBuilder(Schema.Type.RECORD);
        entries.forEach(e -> builder.withEntry(e));

        return builder.build();
    }

    @Suggestions(ACTION_SUGGESTABLE_LOAD_OAUTH2_FORM_PARAMS)
    public SuggestionValues loadOAuth2ProposedFormParams() {
        SuggestionValues suggestionValues = new SuggestionValues();
        suggestionValues.setItems(
                Arrays.asList(new SuggestionValues.Item(OAuth20.Keys.scope.name(), OAuth20.Keys.scope.name())));
        return suggestionValues;
    }

}
