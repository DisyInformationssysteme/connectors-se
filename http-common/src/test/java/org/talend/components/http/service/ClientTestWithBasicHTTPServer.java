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

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.http.TestUtil;
import org.talend.components.http.configuration.Dataset;
import org.talend.components.http.configuration.Datastore;
import org.talend.components.http.configuration.Format;
import org.talend.components.http.configuration.Header;
import org.talend.components.http.configuration.OutputContent;
import org.talend.components.http.configuration.Param;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.auth.Authentication;
import org.talend.components.http.configuration.auth.Authorization;
import org.talend.components.http.configuration.auth.OAuth20;
import org.talend.components.http.server.BasicHTTPServerFactory;
import org.talend.components.http.server.handler.OAuth2TokenHandler;
import org.talend.components.http.service.httpClient.HTTPClientService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.junit5.WithComponents;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Slf4j
@Testcontainers
@WithComponents(value = "org.talend.components.http")
public class ClientTestWithBasicHTTPServer {

    private static HttpServer server;

    private RequestConfig config;

    @Service
    HTTPClientService service;

    @Service
    RecordBuilderService recordBuilderService;

    @BeforeAll
    public static void startServer() {
        server = BasicHTTPServerFactory.getInstance().createServer();
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        server.stop(0);
    }

    private static int getPort() {
        return server.getAddress().getPort();
    }

    private static String getBaseURL() {
        return String.format("http://localhost:%s", getPort());
    }

    @BeforeEach
    public void initConf() {
        config = new RequestConfig();
        Dataset dse = new Dataset();
        Datastore dso = new Datastore();

        dse.setDatastore(dso);
        dse.setMethodType("GET");
        config.setDataset(dse);

        dso.setBase(getBaseURL());
    }

    @Test
    public void testAuthOAUth2Params() throws HTTPClientException {
        config.getDataset().setResource("echo");
        config.getDataset().setFormat(Format.JSON);
        config.getDataset().setReturnedContent(OutputContent.BODY_ONLY);

        OAuth20 oauth2 = new OAuth20();
        oauth2.setTokenEndpoint(getBaseURL() + BasicHTTPServerFactory.HTTP_OAUTH_TOKEN);
        oauth2.setClientId(OAuth2TokenHandler.CLIENT_ID);
        oauth2.setClientSecret(OAuth2TokenHandler.CLIENT_SECRET);
        oauth2.setAuthenticationType(Authorization.OAuth20Authent.FORM);
        oauth2.setFlow(Authorization.OAuth20Flow.CLIENT_CREDENTIAL);
        List<OAuth20.FormParam> params = Arrays.asList(
                new OAuth20.FormParam(
                        org.talend.components.common.httpclient.api.authentication.OAuth20.Keys.scope.name(),
                        OAuth2TokenHandler.SCOPE_READ_WRITE),
                new OAuth20.FormParam("paramA", "aaa"),
                new OAuth20.FormParam("paramB", "111"),
                new OAuth20.FormParam("paramB", "222"),
                new OAuth20.FormParam(
                        org.talend.components.common.httpclient.api.authentication.OAuth20.Keys.scope.name(),
                        "additional"),
                new OAuth20.FormParam("paramA", "bbb"),
                new OAuth20.FormParam("paramB", "333"),
                new OAuth20.FormParam(
                        org.talend.components.common.httpclient.api.authentication.OAuth20.Keys.resource.name(),
                        OAuth2TokenHandler.RESOURCE));
        oauth2.setParams(params);

        config.getDataset().setHasHeaders(true);
        config.getDataset()
                .setHeaders(Arrays.asList(
                        new Header("header1", "for main query", Header.HEADER_QUERY_DESTINATION.MAIN),
                        new Header("header2", "for authent query", Header.HEADER_QUERY_DESTINATION.AUTHENT),
                        new Header("header3", "for main & authent query", Header.HEADER_QUERY_DESTINATION.BOTH),
                        new Header("header4", "for main query 2", Header.HEADER_QUERY_DESTINATION.MAIN),
                        new Header("header5", "for authent query 2", Header.HEADER_QUERY_DESTINATION.AUTHENT),
                        new Header("header6", "for main & authent query 2", Header.HEADER_QUERY_DESTINATION.BOTH)));

        Authentication auth = new Authentication();
        auth.setType(Authorization.AuthorizationType.OAuth20);
        auth.setOauth20(oauth2);

        config.getDataset().getDatastore().setAuthentication(auth);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();

        String authFormString = resp.getRecord("auhent_query").getString("request_body");
        Collection<Param> authFormCollection = TestUtil.queryToCollection(authFormString);

        Record authHeaders = resp.getRecord("auhent_query").getRecord("request_headers");

        Collection<String> authFormExpectedParamA = new ArrayList<>(Arrays.asList("aaa", "bbb"));
        Collection<String> authFormExpectedParamB = new ArrayList<>(Arrays.asList("111", "222", "333"));
        Collection<String> authFormExpectedScope =
                new ArrayList<>(Arrays.asList(OAuth2TokenHandler.SCOPE_READ_WRITE, "additional"));
        Collection<String> authFormExpectedResource = new ArrayList<>(Arrays.asList(OAuth2TokenHandler.RESOURCE));

        authFormCollection.stream().filter(e -> "paramA".equals(e.getKey())).forEach(e -> {
            Assertions.assertTrue(authFormExpectedParamA.remove(e.getValue()));
        });
        Assertions.assertEquals(0, authFormExpectedParamA.size());

        authFormCollection.stream().filter(e -> "paramB".equals(e.getKey())).forEach(e -> {
            Assertions.assertTrue(authFormExpectedParamB.remove(e.getValue()));
        });
        Assertions.assertEquals(0, authFormExpectedParamB.size());

        authFormCollection.stream()
                .filter(e -> org.talend.components.common.httpclient.api.authentication.OAuth20.Keys.scope.name()
                        .equals(e.getKey()))
                .forEach(e -> {
                    Assertions.assertTrue(authFormExpectedScope.remove(e.getValue()));
                });
        Assertions.assertEquals(0, authFormExpectedScope.size());

        authFormCollection.stream()
                .filter(e -> org.talend.components.common.httpclient.api.authentication.OAuth20.Keys.resource.name()
                        .equals(e.getKey()))
                .forEach(e -> {
                    Assertions.assertTrue(authFormExpectedResource.remove(e.getValue()));
                });
        Assertions.assertEquals(0, authFormExpectedResource.size());

        Optional<String> header1 = authHeaders.getOptionalString("header1");
        Optional<String> header2 = authHeaders.getOptionalString("header2");
        Optional<String> header3 = authHeaders.getOptionalString("header3");
        Optional<String> header4 = authHeaders.getOptionalString("header4");
        Optional<String> header5 = authHeaders.getOptionalString("header5");
        Optional<String> header6 = authHeaders.getOptionalString("header6");

        Assertions.assertFalse(header1.isPresent());
        Assertions.assertEquals("for authent query", header2.get());
        Assertions.assertEquals("for main & authent query", header3.get());
        Assertions.assertFalse(header4.isPresent());
        Assertions.assertEquals("for authent query 2", header5.get());
        Assertions.assertEquals("for main & authent query 2", header6.get());

        assertFalse(respIt.hasNext());
    }

}
