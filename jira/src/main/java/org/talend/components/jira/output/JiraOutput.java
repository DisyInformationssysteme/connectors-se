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
package org.talend.components.jira.output;

import java.util.Collections;
import java.util.Optional;

import org.apache.cxf.common.util.StringUtils;
import org.talend.components.common.httpclient.api.BodyFormat;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.http.configuration.Dataset;
import org.talend.components.http.configuration.Datastore;
import org.talend.components.http.configuration.Header;
import org.talend.components.http.configuration.OutputContent;
import org.talend.components.http.configuration.Param;
import org.talend.components.http.configuration.RequestBody;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.auth.Authentication;
import org.talend.components.http.configuration.auth.Authorization;
import org.talend.components.http.configuration.auth.Basic;
import org.talend.components.http.output.AbstractHTTPOutput;
import org.talend.components.http.service.I18n;
import org.talend.components.http.service.httpClient.HTTPClientService;
import org.talend.components.jira.dataset.ResourceType;
import org.talend.components.jira.datastore.JiraDatastore;
import org.talend.components.jira.service.JiraI18n;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.exception.ComponentException;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Input;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.record.Record;

import lombok.extern.slf4j.Slf4j;

@Version
@Icon(value = Icon.IconType.CUSTOM, custom = "jira-output")
@Processor(name = "Output")
@Documentation("Jira output.")
@Slf4j
public class JiraOutput extends AbstractHTTPOutput<JiraOutputConfiguration> {

    private JiraOutputConfiguration configuration;

    private final JiraI18n jiraI18n;

    public JiraOutput(@Option("configuration") final JiraOutputConfiguration configuration,
            final HTTPClientService httpClientService,
            final I18n i18n, final JiraI18n jiraI18n) {
        super(configuration, httpClientService, i18n);
        this.configuration = configuration;
        this.jiraI18n = jiraI18n;
    }

    @Override
    protected RequestConfig translateConfiguration(JiraOutputConfiguration configuration) {
        RequestConfig requestConfig = new RequestConfig();

        Datastore requestConfigDatastore = new Datastore();
        requestConfigDatastore.setBase(configuration.getDataset().getDatastore().getJiraURL());

        Authentication authentication = new Authentication();
        if (configuration.getDataset()
                .getDatastore()
                .getAuthenticationType() == JiraDatastore.AuthenticationType.BASIC) {
            authentication.setType(Authorization.AuthorizationType.Basic);
            Basic basic = new Basic();
            basic.setUsername(configuration.getDataset().getDatastore().getUser());
            basic.setPassword(configuration.getDataset().getDatastore().getPass());
            authentication.setBasic(basic);
        } else if (configuration.getDataset()
                .getDatastore()
                .getAuthenticationType() == JiraDatastore.AuthenticationType.PAT) {
            authentication.setType(Authorization.AuthorizationType.Bearer);
            authentication.setBearerToken(configuration.getDataset().getDatastore().getPat());
        }
        requestConfigDatastore.setAuthentication(authentication);

        Dataset requestDataset = new Dataset();
        requestDataset.setDatastore(requestConfigDatastore);
        Header hostHeader = new Header("Host", configuration.getDataset().getDatastore().getJiraURL(), Header.HEADER_QUERY_DESTINATION.MAIN);
        requestDataset.setHasHeaders(true);
        requestDataset.setHeaders(Collections.singletonList(hostHeader));
        switch (configuration.getOutputAction()) {
        case CREATE:
            requestDataset.setMethodType("POST");
            break;
        case UPDATE:
            requestDataset.setMethodType("PUT");
            break;
        case DELETE:
            requestDataset.setMethodType("DELETE");
            break;
        }

        requestConfig.setDieOnError(false);
        requestConfig.setDataset(requestDataset);

        if (configuration.getOutputAction() != OutputAction.DELETE) {
            requestConfig.getDataset().setHasBody(true);
            RequestBody requestBody = new RequestBody();
            requestBody.setType(BodyFormat.JSON);

            requestConfig.getDataset().setBody(requestBody);
        }

        if (configuration.getOutputAction() == OutputAction.DELETE) {
            requestConfig.getDataset().setHasQueryParams(true);
            requestConfig.getDataset()
                    .setQueryParams(Collections.singletonList(new Param("deleteSubtasks",
                            String.valueOf(configuration.isDeleteSubtasks()))));
        }

        return requestConfig;
    }

    @Override
    @ElementListener
    public void process(@Input Record input) {
        validateRecordSchema(input); // can be removed when static schema support added in studio
        processRequestConfigWithInput(input);
        super.process(input);

        processRequestResult();
    }

    private void validateRecordSchema(Record input) {
        Optional<String> idColumn = input.getOptionalString("id");
        Optional<String> jsonColumn = input.getOptionalString("json");
        switch (configuration.getOutputAction()) {
        case CREATE:
            jsonColumn.orElseThrow(() -> new ComponentException(jiraI18n.incorrectSchemaCreate()));
            break;
        case UPDATE:
            jsonColumn.orElseThrow(() -> new ComponentException(jiraI18n.incorrectSchemaUpdate()));
            idColumn.orElseThrow(() -> new ComponentException(jiraI18n.incorrectSchemaUpdate()));
            break;
        case DELETE:
            idColumn.orElseThrow(() -> new ComponentException(jiraI18n.incorrectSchemaDelete()));
        }
    }

    private void processRequestConfigWithInput(Record input) {
        getConfig().getDataset().setResource(getOutputResourcePath(configuration, input));
        if (configuration.getOutputAction() != OutputAction.DELETE) {
            getConfig().getDataset().getBody().setJsonValue(input.getString("json"));
        }
    }

    private void processRequestResult() {
        HTTPClient.HTTPResponse response = getLastServerResponse();
        if (response != null) {
            if (response.getStatus().getCode() / 100 > 3) {
                StringBuilder errorMessage = new StringBuilder(response.getStatus().getCodeWithReason());
                try {
                    String errorResponseBody = response.getBodyAsString();
                    if (!StringUtils.isEmpty(errorResponseBody)) {
                        errorMessage.append(System.lineSeparator()).append(errorResponseBody);
                    }
                } catch (HTTPClientException cantGetBodyAsStringException) {
                    log.debug("Can't read error response body, just code would be in error message",
                            cantGetBodyAsStringException);
                }
                throw new ComponentException(getI18n().responseStatusIsNotOK(errorMessage.toString()));
            }
        }
    }

    private String getOutputResourcePath(JiraOutputConfiguration configuration, Record input) {
        if (configuration.getDataset().getResourceType() == ResourceType.ISSUE) {
            switch (configuration.getOutputAction()) {
            case CREATE:
                return "rest/api/2/issue/";
            case UPDATE:
            case DELETE:
            default:
                return "rest/api/2/issue/" + input.getString("id");
            }
        } else {
            switch (configuration.getOutputAction()) {
            case CREATE:
                return "rest/api/2/project/";
            case UPDATE:
            case DELETE:
            default:
                return "rest/api/2/project/" + input.getString("id");
            }
        }
    }
}
