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
package org.talend.components.common.httpclient.impl.cxf;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.cxf.helpers.HttpHeaderHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.KeyValuePair;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.common.httpclient.api.QueryConfigurationBuilder;
import org.talend.components.common.httpclient.api.ResponseFormat;
import org.talend.components.common.httpclient.api.authentication.OAuth20;
import org.talend.components.common.httpclient.api.authentication.OAuth20FlowExecution;
import org.talend.components.common.httpclient.api.authentication.Token;
import org.talend.components.common.httpclient.api.pagination.PaginationParametersLocation;
import org.talend.components.common.httpclient.factory.HTTPClientFactory;
import org.talend.components.common.httpclient.impl.cxf.servers.AbstractHTTPServerFactory;
import org.talend.components.common.httpclient.impl.cxf.servers.BasicHTTPServerFactory;

class CXFHTTPClientImplWithBasicHTTPServerTest {

    private final static AbstractHTTPServerFactory.TestHTTPServer server =
            BasicHTTPServerFactory.getInstance().createServer();

    private QueryConfiguration queryConfiguration;

    @Test
    public void simpleTest() throws HTTPClientException {
        QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_SIMPLE)).build();

        HTTPClient httpClient = HTTPClientFactory.create(config);
        HTTPClient.HTTPResponse response = httpClient.invoke();
        String body = response.getBodyAsString();

        Assertions.assertEquals(ResourcesUtils.loadResource("/responses/simple.json"), body);

    }

    @Test
    public void urlEncodedForm() throws HTTPClientException {
        String comment = "<p>This is a <em>description</emp> &to check URL encode of form parameters: !=&éè.</p>";
        String name = "Raphaël";
        String age = "33";
        String hobby = "skating";

        QueryConfiguration config = QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                .setMethod("POST")
                .addXWWWFormURLEncodedBodyParam("name", name)
                .addXWWWFormURLEncodedBodyParam("age", age)
                .addXWWWFormURLEncodedBodyParam("description", comment)
                .addXWWWFormURLEncodedBodyParam("hobby", hobby)
                .build();
        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        JsonObject jsonBody = ResourcesUtils.getJsonObject(response.getBodyAsString());

        Assertions.assertEquals(MediaType.APPLICATION_FORM_URLENCODED,
                jsonBody.getJsonObject("request-headers").getString(HttpHeaderHelper.CONTENT_TYPE.toLowerCase()));
        String encodedForm =
                "name=Rapha%C3%ABl&age=33&description=%3Cp%3EThis+is+a+%3Cem%3Edescription%3C%2Femp%3E+%26to+check+URL+encode+of+form+parameters%3A+%21%3D%26%C3%A9%C3%A8.%3C%2Fp%3E&hobby=skating";
        Assertions.assertEquals(encodedForm, jsonBody.getString("request-body"));
    }

    @Test
    public void multiPartFormData() throws HTTPClientException {
        String comment = "<p>This is a <em>description</emp> &to check URL encode of form parameters: !=&éè.</p>";
        String name = "Raphaël";
        String age = "33";
        String hobby = "skating";

        QueryConfiguration config = QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                .setMethod("POST")
                .addMultipartFormDataBodyParam("name", name)
                .addMultipartFormDataBodyParam("age", age)
                .addMultipartFormDataBodyParam("description", comment)
                .addMultipartFormDataBodyParam("hobby", hobby)
                .build();
        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        JsonObject jsonBody = ResourcesUtils.getJsonObject(response.getBodyAsString());

        String expectedContentType = String.format("%s; boundary=\"uuid:", MediaType.MULTIPART_FORM_DATA);
        Assertions.assertTrue(
                jsonBody.getJsonObject("request-headers")
                        .getString(HttpHeaderHelper.CONTENT_TYPE.toLowerCase())
                        .startsWith(expectedContentType),
                String.format("%s %s header, doesn't start with '%s'.", MediaType.MULTIPART_FORM_DATA,
                        HttpHeaderHelper.CONTENT_TYPE, expectedContentType));

        String expectedBody = "\n--uuid:a41b33ca-bcf3-48df-ad74-1e5295406516\n" +
                "Content-Type: text/plain\n" +
                "Content-Transfer-Encoding: binary\n" +
                "Content-ID: <name>\n" +
                "Content-Disposition: form-data; name=\"name\"\n" +
                "\n" +
                "Raphaël\n" +
                "--uuid:a41b33ca-bcf3-48df-ad74-1e5295406516\n" +
                "Content-Type: text/plain\n" +
                "Content-Transfer-Encoding: binary\n" +
                "Content-ID: <age>\n" +
                "Content-Disposition: form-data; name=\"age\"\n" +
                "\n" +
                "33\n" +
                "--uuid:a41b33ca-bcf3-48df-ad74-1e5295406516\n" +
                "Content-Type: text/plain\n" +
                "Content-Transfer-Encoding: binary\n" +
                "Content-ID: <description>\n" +
                "Content-Disposition: form-data; name=\"description\"\n" +
                "\n" +
                "<p>This is a <em>description</emp> &to check URL encode of form parameters: !=&éè.</p>\n" +
                "--uuid:a41b33ca-bcf3-48df-ad74-1e5295406516\n" +
                "Content-Type: text/plain\n" +
                "Content-Transfer-Encoding: binary\n" +
                "Content-ID: <hobby>\n" +
                "Content-Disposition: form-data; name=\"hobby\"\n" +
                "\n" +
                "skating\n" +
                "--uuid:a41b33ca-bcf3-48df-ad74-1e5295406516--";

        String regexUUIDBoundary = "--uuid:[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+";
        String replaceBoundary = "--uuid:<REPLACED UUID>";
        expectedBody = expectedBody.replaceAll(regexUUIDBoundary, replaceBoundary);

        Assertions.assertEquals(expectedBody,
                jsonBody.getString("request-body").replaceAll(regexUUIDBoundary, replaceBoundary));

    }

    @ParameterizedTest
    /*
     * TODO: Need to wait for https://issues.apache.org/jira/browse/CXF-8752
     *
     * @CsvSource(value = { "GET", "HEAD", "OPTIONS", "TRACE", "POST", "PUT" }):
     */
    @CsvSource(value = { "GET", "HEAD", "OPTIONS", "TRACE" })
    public void redirectTest(String method) throws HTTPClientException {
        QueryConfiguration config = QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_REDIRECT))
                .setMethod(method)
                .addQueryParam("nb_redirect", "5")
                .addQueryParam("dec", "true")
                // .setAllowedRedirectedVerbs(method)
                .acceptRelativeURLRedirection(true)
                .setMaxNumberOfAcceptedRedirectionsOnSameURI(10)
                // .setRawTextBody("Hello") // Need to wait for https://issues.apache.org/jira/browse/CXF-8752
                .build();
        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();

        /*
         * Need to wait for https://issues.apache.org/jira/browse/CXF-8752
         * String body = ResourcesUtils.getString(response.getBodyAsStream());
         * Assertions.assertEquals("Hello", body)
         */

        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());
    }

    @Test
    public void exceededRedirectionTest() {
        Assertions.assertThrows(HTTPClientException.class, () -> {
            QueryConfiguration config = QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_REDIRECT))
                    .setMethod("GET")
                    .addQueryParam("nb_redirect", "5")
                    .addQueryParam("dec", "false")
                    .setMaxNumberOfAcceptedRedirectionsOnSameURI(3)
                    .build();
            HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        }, "Max number of redirections to same query exception was expected.");
    }

    @Test
    public void acceptOnlyRedirectionToSameHost() {
        Assertions.assertThrows(HTTPClientException.class, () -> {
            QueryConfiguration config =
                    QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_REDIRECT_CONFIGURE_HOST))
                            .setMethod("GET")
                            .addQueryParam("to", "https://anotherhost.com")
                            .acceptOnlySameHostRedirection(true)
                            .build();
            HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        }, "Accept only redirections to same host exception was expected.");
    }

    @ParameterizedTest
    @CsvSource({ "1000,1,true", "10,500,false" })
    public void receiveTimeoutTest(long receiveTimeout, long serverDelay, boolean success) throws HTTPClientException {
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_DELAYED_RESPONSE))
                        .setMethod("GET")
                        .setReceiveTimeout(receiveTimeout)
                        .addQueryParam("delay", Long.toString(serverDelay))
                        .build();

        if (success) {
            HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
            Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());
        } else {
            Assertions.assertThrows(HTTPClientException.class, () -> {
                HTTPClientFactory.create(config).invoke();
            }, "A receive timeout exception should have been thrown.");
        }
    }

    @Test
    public void connectionTimeoutTest() throws HTTPClientException {
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_DELAYED_RESPONSE))
                        .setMethod("GET")
                        .setConnectionTimeout(2000)
                        .addQueryParam("delay", "500")
                        .build();

        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());

        // 172.31.255.255 is not routable and should generate a connection time out.
        final QueryConfiguration failingConfig = QueryConfigurationBuilder.create("https://172.31.255.255")
                .setMethod("GET")
                .setConnectionTimeout(500)
                .build();

        Assertions.assertThrows(HTTPClientException.class, () -> {
            HTTPClientFactory.create(failingConfig).invoke();
        }, "A connection timeout exception should have been thrown.");

    }

    @Test
    public void basicAuthentication() throws HTTPClientException {
        String myPassword = "myPassword";
        String myUser = "myUser";
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                        .setMethod("GET")
                        .setBasicAuthentication(myUser, myPassword)
                        .build();

        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        JsonObject jsonObject = ResourcesUtils.getJsonObject(response.getBodyAsString());
        JsonObject headers = jsonObject.getJsonObject("request-headers");
        String token = headers.getString(HttpHeaderHelper.AUTHORIZATION.toLowerCase());

        // This is a test local credential, it can be public
        Assertions.assertEquals("Basic bXlVc2VyOm15UGFzc3dvcmQ=", token);
    }

    @Test
    public void oauth20ClientCredentialAdditionalParameters() {
        QueryConfigurationBuilder queryConfigurationBuilder =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_OAUTH_RESOURCE))
                        .setMethod("GET")
                        .setOAuth20ClientCredential(OAuth20.AuthentMode.FORM,
                                getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_FORM_TOKEN),
                                BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                                BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_SECRET,
                                Arrays.asList(new KeyValuePair(OAuth20.Keys.scope.name(), "my_resource_read"),
                                        new KeyValuePair("enterprise_id", "1234"),
                                        new KeyValuePair(OAuth20.Keys.scope.name(), "my_resource_write"),
                                        new KeyValuePair("another", "abcde"),
                                        new KeyValuePair("enterprise_id", "5678"),
                                        new KeyValuePair("another", "fghi")),
                                Collections.emptyList());

        QueryConfiguration query = queryConfigurationBuilder.build();

        OAuth20FlowExecution oAuth20FlowExecution = new OAuth20FlowExecution(query.getOauthCall());
        QueryConfiguration config = oAuth20FlowExecution.getConfig();
        List<KeyValuePair> bodyQueryParams = config.getBodyQueryParams();

        List<String> expectedScopes = new ArrayList<>(Arrays.asList("my_resource_read", "my_resource_write"));
        List<String> expectedEnterpriseId = new ArrayList<>(Arrays.asList("1234", "5678"));
        List<String> expectedAnother = new ArrayList<>(Arrays.asList("abcde", "fghi"));

        bodyQueryParams.stream()
                .filter(p -> OAuth20.Keys.scope.name().equals(p.getKey()))
                .map(e -> e.getValue())
                .forEach(e -> Assertions.assertTrue(expectedScopes.remove(e)));
        Assertions.assertEquals(0, expectedScopes.size());

        bodyQueryParams.stream()
                .filter(p -> "enterprise_id".equals(p.getKey()))
                .map(e -> e.getValue())
                .forEach(e -> Assertions.assertTrue(expectedEnterpriseId.remove(e)));
        Assertions.assertEquals(0, expectedEnterpriseId.size());

        bodyQueryParams.stream()
                .filter(p -> "another".equals(p.getKey()))
                .map(e -> e.getValue())
                .forEach(e -> Assertions.assertTrue(expectedAnother.remove(e)));
        Assertions.assertEquals(0, expectedAnother.size());
    }

    @ParameterizedTest
    @MethodSource("oauth20ClientCredentialAuthenticationParams")
    public void oauth20ClientCredentialAuthentication(OAuth20.AuthentMode authentMode, String tokenEndpoint,
            String clientId, String clientSecret, boolean exception, Token oauthToken, String expectedToken)
            throws HTTPClientException {

        QueryConfigurationBuilder queryConfigurationBuilder =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_OAUTH_RESOURCE))
                        .setMethod("GET")
                        .setOAuth20ClientCredential(authentMode,
                                tokenEndpoint,
                                clientId,
                                clientSecret,
                                Collections.emptyList(),
                                Collections.emptyList());

        if (expectedToken != null) {
            queryConfigurationBuilder.addQueryParam(BasicHTTPServerFactory.EXPECTED_TOKEN_KEY, expectedToken);
        }

        final QueryConfiguration config = queryConfigurationBuilder.build();

        if (exception) {
            HTTPClientException httpClientException = Assertions.assertThrowsExactly(HTTPClientException.class, () -> {
                HTTPClientFactory.create(config).invoke();
            });
            String expectedMessage = ResourcesUtils.loadResource("/expectedMessages/oauth20ClientCredentialsError.txt");
            Assertions.assertEquals(expectedMessage, httpClientException.getMessage());
        } else {
            HTTPClient httpClientConfig = HTTPClientFactory.create(config);
            if (oauthToken != null) {
                httpClientConfig.setOAuth20Token(oauthToken);
            }
            HTTPClient.HTTPResponse response = httpClientConfig.invoke();
            Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());

            if (oauthToken != null) {
                Token t = (Token) response.getOAuth20Token().get();
                Assertions.assertEquals(oauthToken.getAccessToken(), t.getAccessToken());
                Assertions.assertEquals(oauthToken.getTokenType(), t.getTokenType());
                Assertions.assertEquals(oauthToken.getDeliveredTime(), t.getDeliveredTime());
                Assertions.assertEquals(oauthToken.getExpiresIn(), t.getExpiresIn());
            }
        }
    }

    public static Stream<Arguments> oauth20ClientCredentialAuthenticationParams() {
        Token t = new Token("12345", "type", System.currentTimeMillis(), 60);

        return Stream.of(
                Arguments.of(OAuth20.AuthentMode.FORM,
                        getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_FORM_TOKEN),
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_SECRET,
                        false,
                        null,
                        null),
                Arguments.of(OAuth20.AuthentMode.FORM,
                        getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_FORM_TOKEN),
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                        "wrong_secret",
                        true,
                        null,
                        null),
                Arguments.of(OAuth20.AuthentMode.BASIC,
                        getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_BASIC_TOKEN),
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_SECRET,
                        false,
                        null,
                        null),
                Arguments.of(OAuth20.AuthentMode.BASIC,
                        getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_BASIC_TOKEN),
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                        "wrong_secret",
                        true,
                        null,
                        null),
                Arguments.of(OAuth20.AuthentMode.FORM,
                        getUrl(BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_FORM_TOKEN),
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_ID,
                        BasicHTTPServerFactory.HTTP_OAUTH_CLIENT_CREDENTIALS_TOKEN_CLIENT_SECRET,
                        false,
                        t,
                        String.format("%s %s", t.getTokenType(), t.getAccessToken())));
    }

    @Test
    public void authorizationTokenAuthentication() throws HTTPClientException {
        String prefix = "Bearer";
        String token = "1234567890abcDEF";
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                        .setMethod("GET")
                        .setAuthorizationToken(prefix, token)
                        .build();

        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        JsonObject jsonObject = ResourcesUtils.getJsonObject(response.getBodyAsString());
        JsonObject headers = jsonObject.getJsonObject("request-headers");
        String value = headers.getString(HttpHeaderHelper.AUTHORIZATION.toLowerCase());

        Assertions.assertEquals(String.format("%s %s", prefix, token), value);

        final QueryConfiguration configNoPrefix =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                        .setMethod("GET")
                        .setAuthorizationToken(token)
                        .build();

        HTTPClient.HTTPResponse responseNoPrefix = HTTPClientFactory.create(configNoPrefix).invoke();
        JsonObject jsonObjectNoPrefix = ResourcesUtils.getJsonObject(responseNoPrefix.getBodyAsString());
        JsonObject headersNoPrefix = jsonObjectNoPrefix.getJsonObject("request-headers");
        String valueNoPrefix = headersNoPrefix.getString(HttpHeaderHelper.AUTHORIZATION.toLowerCase());

        Assertions.assertEquals(token, valueNoPrefix);
    }

    @Test
    public void validateSite() throws HTTPClientException {
        System.setProperty("connectors.enable_local_network_access", "false");

        Assertions.assertThrows(HTTPClientException.class, () -> {
            final QueryConfiguration config =
                    QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                            .setMethod("GET")
                            .build();

            HTTPClientFactory.create(config).invoke();
        });
    }

    @ParameterizedTest
    @CsvSource({ "true", "false" })
    public void contentEncodingGZIPBodyQuery(boolean supportGzip) throws HTTPClientException {
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_GZIP))
                        .setMethod("GET")
                        .decompressResponsePayload(supportGzip)
                        .build();

        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();
        String payload = response.getBodyAsString();
        if (supportGzip) {
            Assertions.assertEquals(BasicHTTPServerFactory.HELLO_WORLD, payload);
        } else {
            Assertions.assertEquals(
                    "\u001F�\b\u0000\u0000\u0000\u0000\u0000\u0000\u0000�H���W(�/�IQ\u0004\u0000�\u0019�\u001B\f\u0000\u0000\u0000",
                    payload);
        }
    }

    @ParameterizedTest
    @EnumSource(ResponseFormat.class)
    public void responseFormatTest(ResponseFormat f) throws HTTPClientException {
        final QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_ECHO))
                        .setMethod("GET")
                        .setResponseFormat(f)
                        .build();

        HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();

        JsonObject jsonObject = ResourcesUtils.getJsonObject(response.getBodyAsString());
        JsonObject headers = jsonObject.getJsonObject("request-headers");
        String value = headers.getString(HttpHeaders.ACCEPT.toLowerCase());

        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());
        Assertions.assertEquals(f.getAcceptedType(), value);
    }

    @ParameterizedTest
    @CsvSource({ "QUERY_PARAMETERS,0,3",
            "QUERY_PARAMETERS,40,10",
            "HEADERS,40,4",
            "HEADERS,1,6", })
    public void offsetLimitPagination(PaginationParametersLocation location, int offset, int limit)
            throws HTTPClientException {

        QueryConfiguration config =
                QueryConfigurationBuilder.create(getUrl(BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT))
                        .setMethod("GET")
                        .setOffsetLimitPagination(location,
                                BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT_OFFSETNAME,
                                String.valueOf(offset),
                                BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT_LIMITNAME,
                                String.valueOf(limit),
                                BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT_ELEMENTS)
                        .addQueryParam(BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT_LOCALISATION,
                                location.name())
                        .build();

        List<JsonObject> allElements = new ArrayList<>();
        Optional<QueryConfiguration> optionalQueryConfiguration = null;
        do {
            if (optionalQueryConfiguration != null) {
                config = optionalQueryConfiguration.get();
            }
            HTTPClient.HTTPResponse response = HTTPClientFactory.create(config).invoke();

            JsonReader reader = Json.createReader(new StringReader(response.getBodyAsString()));
            JsonArray elements =
                    reader.readObject().getJsonArray(BasicHTTPServerFactory.HTTP_PAGINATION_OFFSET_LIMIT_ELEMENTS);
            elements.stream().forEach(e -> allElements.add((JsonObject) e));

            optionalQueryConfiguration = response.nextPageQueryConfiguration();
        } while (optionalQueryConfiguration.isPresent());

        Assertions.assertEquals(53 - offset, allElements.size());

        for (int i = 0; i < allElements.size(); i++) {
            JsonObject e = allElements.get(i);
            int id = i + offset + 1;
            Assertions.assertEquals(id, e.getInt("id"));
            Assertions.assertEquals("name_" + id, e.getString("name"));
        }

    }

    private static String getUrl(String endPoint) {
        return getBaseURL() + endPoint;
    }

    private static String getBaseURL() {
        return String.format("http://localhost:%s", server.getPort());
    }

    @BeforeEach
    public void beforeEach() {
        System.setProperty("connectors.enable_local_network_access", "true");
    }

    @BeforeAll
    public static void init() {
        server.start();
    }

    @AfterAll
    public static void release() {
        server.stop();
    }

}
