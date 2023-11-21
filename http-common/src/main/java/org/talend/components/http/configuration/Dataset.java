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
package org.talend.components.http.configuration;

import lombok.Data;
import org.talend.components.extension.polling.api.PollableDuplicateDataset;
import org.talend.components.http.configuration.pagination.Pagination;
import org.talend.components.http.migration.HttpClientDatasetMigrationHandler;
import org.talend.components.http.service.UIService;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.action.Updatable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Min;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Version(value = Datastore.VERSION, migrationHandler = HttpClientDatasetMigrationHandler.class)
@DataSet("Dataset")
@PollableDuplicateDataset
@GridLayout({ @GridLayout.Row({ "datastore" }), //
        @GridLayout.Row({ "methodType" }), //
        @GridLayout.Row({ "resource" }), //
        @GridLayout.Row({ "hasPathParams" }), //
        @GridLayout.Row({ "pathParams" }), //
        @GridLayout.Row({ "hasQueryParams" }), //
        @GridLayout.Row({ "queryParams" }), //
        @GridLayout.Row({ "hasHeaders" }), //
        @GridLayout.Row({ "headers" }), //
        @GridLayout.Row({ "hasBody" }), //
        @GridLayout.Row({ "body" }), //
        @GridLayout.Row({ "format", "dssl" }),
        @GridLayout.Row({ "returnedContent" }),
        @GridLayout.Row({ "outputKeyValuePairs", "forwardInput" }),
        @GridLayout.Row({ "keyValuePairs" })
})

@GridLayout(names = GridLayout.FormType.ADVANCED, value = { @GridLayout.Row({ "datastore" }),
        @GridLayout.Row({ "acceptRedirections" }), //
        @GridLayout.Row({ "maxRedirectOnSameURL" }), //
        @GridLayout.Row({ "onlySameHost" }), //
        @GridLayout.Row({ "hasPagination" }), //
        @GridLayout.Row({ "pagination" }), //
        // @GridLayout.Row({ "force302Redirect" }), // TODO: https://jira.talendforge.org/browse/TDI-48326
        @GridLayout.Row({ "jsonForceDouble" }),
        @GridLayout.Row({ "enforceNumberAsString" }) })
@Documentation("HTTP dataset configuration.")
public class Dataset implements Serializable {

    @Option
    @Documentation("Identification of the REST API.")
    private Datastore datastore = new Datastore();

    @Option
    @Required
    @DefaultValue("GET")
    @Documentation("The HTTP verb to use.")
    @Suggestable(value = UIService.ACTION_HTTP_METHOD_LIST, parameters = "connection")
    private String methodType;

    @Option
    @Required
    @Documentation("End of url to complete base url of the datastore.")
    private String resource = "";

    @Option
    @Required
    @Documentation("Format of the body's answer.")
    private Format format = Format.RAW_TEXT;

    @Option("dssl")
    @Documentation("JSONPointer/DSSL selector to compute only a sub part of a response document (JSON).")
    @ActiveIf(target = "format", value = "JSON")
    @DefaultValue("")
    private String selector = "";

    @Option
    @Documentation("If answer body type is JSON, infer numbers type or force all to double.")
    @ActiveIf(target = "format", value = "JSON")
    @DefaultValue("true")
    private boolean jsonForceDouble = true;

    @Option
    @Documentation("If answer body type is XML, force all numbers as string.")
    @ActiveIf(target = "format", value = "XML")
    @DefaultValue("true")
    private boolean enforceNumberAsString = true;

    @Option
    @Documentation("Accept redirections.")
    @DefaultValue("true")
    private boolean acceptRedirections = true;

    @Option
    @Documentation("Number of accepted redirections on same URI.")
    @ActiveIf(target = "acceptRedirections", value = "true")
    @DefaultValue("3")
    @Min(0)
    @Required
    private Integer maxRedirectOnSameURL = 3;

    @Option
    @Documentation("Redirect only if same host.")
    @DefaultValue("false")
    @ActiveIf(target = "acceptRedirections", value = "true")
    private boolean onlySameHost = false;

    @Option
    @Documentation("Force a GET on a 302 redirection.")
    @DefaultValue("false")
    @ActiveIf(target = "acceptRedirections", value = "true")
    private boolean force302Redirect = false;

    @Option
    @Documentation("Activate to define URL path parameters.")
    private boolean hasPathParams = false;

    @Option
    @ActiveIf(target = "hasPathParams", value = "true")
    @Documentation("Path parameters.")
    private List<Param> pathParams = new ArrayList<>();

    @Option
    @Documentation("Activate to define headers.")
    private boolean hasHeaders = false;

    @Option
    @ActiveIf(target = "hasHeaders", value = "true")
    @Documentation("Query headers.")
    private List<Header> headers = new ArrayList<>();

    @Option
    @Documentation("Activate to define query parameters.")
    private boolean hasQueryParams = false;

    @Option
    @ActiveIf(target = "hasQueryParams", value = "true")
    @Documentation("Query parameters.")
    private List<Param> queryParams = new ArrayList<>();

    @Option
    @Documentation("Activate to define the body.")
    private boolean hasBody;

    @Option
    @ActiveIf(target = "hasBody", value = "true")
    @Documentation("Request body.")
    private RequestBody body;

    @Option
    @Required
    @Documentation("Define the content of the returned record.")
    @DefaultValue("BODY_ONLY")
    private OutputContent returnedContent = OutputContent.BODY_ONLY;

    @Option
    @Documentation("Create key/value pairs to help the creation of a kind of context that will be used in HTTP calls.")
    @DefaultValue("false")
    private boolean outputKeyValuePairs = false;

    @Option
    @Documentation("Complete incoming context.")
    @ActiveIf(target = "outputKeyValuePairs", value = "true")
    private boolean forwardInput = false;

    @Option
    @Documentation("New entries to add in the context.")
    @ActiveIf(target = "outputKeyValuePairs", value = "true")
    private List<Param> keyValuePairs = new ArrayList<>();

    @Option
    @Documentation("Activate to configuration pagination support.")
    private boolean hasPagination;

    @Option
    @Documentation("Pagination configuration.")
    @ActiveIf(target = "hasPagination", value = "true")
    @Updatable(value = UIService.ACTION_UPDATE_PAGINATION_PRESET, parameters = { "pagination" },
            after = "preset")
    private Pagination pagination;

}
