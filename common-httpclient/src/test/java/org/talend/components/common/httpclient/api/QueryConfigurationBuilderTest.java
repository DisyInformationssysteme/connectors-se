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
package org.talend.components.common.httpclient.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.talend.components.common.httpclient.api.authentication.APIKeyDestination;
import org.talend.components.common.httpclient.api.authentication.AuthenticationType;
import org.talend.components.common.httpclient.api.authentication.OAuth20;
import org.talend.components.common.httpclient.api.substitutor.MapDictionary;
import org.talend.components.common.httpclient.api.substitutor.Substitutor;

class QueryConfigurationBuilderTest {

    public final static String HEADER_CONTENT_TYPE_KEY = "Content-Type";

    @Test
    public void createQueryConfigurationTest() {
        String url = "https://myurl.com";
        String body = "This is a raw text body.";
        String method = "PATCH";
        int connectionTimeout = 222;
        int receiveTimeout = 333;

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("header1", "header1_val");
        expectedHeaders.put("header2", "header2_val");

        Map<String, String> expectedQueryParams = new HashMap<>();
        expectedQueryParams.put("query1", "query1_val");
        expectedQueryParams.put("query2", "query2_val");

        Map<String, String> expectedURLPathParms = new HashMap<>();
        expectedURLPathParms.put("path1", "path1_val");
        expectedURLPathParms.put("path2", "path2_val");

        QueryConfigurationBuilder queryConfigurationBuilder = QueryConfigurationBuilder.create(url)
                .setRawTextBody(body)
                .setMethod(method)
                .setConnectionTimeout(connectionTimeout)
                .setReceiveTimeout(receiveTimeout)
                .acceptRedirection(false);

        expectedHeaders.entrySet().forEach(h -> queryConfigurationBuilder.addHeader(h.getKey(), h.getValue()));
        expectedQueryParams.entrySet().forEach(h -> queryConfigurationBuilder.addQueryParam(h.getKey(), h.getValue()));
        expectedURLPathParms.entrySet().forEach(h -> queryConfigurationBuilder.addPathParam(h.getKey(), h.getValue()));
        QueryConfiguration config = queryConfigurationBuilder.build();

        Assertions.assertEquals(url, config.getUrl());
        Assertions.assertEquals(body, config.getPlainTextBody());
        Assertions.assertEquals(BodyFormat.TEXT, config.getBodyType());
        Assertions.assertEquals(method, config.getMethod());

        Map<String, String> headers =
                config.getHeaders().stream().collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue()));
        Assertions.assertEquals(expectedHeaders, headers);

        Map<String, String> queryParams =
                config.getQueryParams().stream().collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue()));
        Assertions.assertEquals(expectedQueryParams, queryParams);

        Assertions.assertEquals(expectedURLPathParms, config.getUrlPathParams());

        Assertions.assertEquals(connectionTimeout, config.getConnectionTimeout());
        Assertions.assertEquals(receiveTimeout, config.getReceiveTimeout());

        Assertions.assertFalse(config.isAcceptRedirections());
    }

    @Test
    public void bodyTextTest() {
        String body = "/* Content is not checked */";

        QueryConfiguration txt = QueryConfigurationBuilder.create("https://myurl.com")
                .setRawTextBody(body)
                .build();
        Assertions.assertEquals(body, txt.getPlainTextBody());
        Assertions.assertEquals(BodyFormat.TEXT, txt.getBodyType());

        QueryConfiguration json = QueryConfigurationBuilder.create("https://myurl.com")
                .setJSONBody(body)
                .build();
        Assertions.assertEquals(body, json.getPlainTextBody());
        Assertions.assertEquals(BodyFormat.JSON, json.getBodyType());

        QueryConfiguration xml = QueryConfigurationBuilder.create("https://myurl.com")
                .setXMLBody(body)
                .build();
        Assertions.assertEquals(body, xml.getPlainTextBody());
        Assertions.assertEquals(BodyFormat.XML, xml.getBodyType());
    }

    @Test
    public void bodyFormDataTest() {
        Map<String, String> keyVals = new HashMap<>();
        keyVals.put("name", "peter");
        keyVals.put("age", "30");
        keyVals.put("company", "Talend");

        final QueryConfigurationBuilder formBuilder = QueryConfigurationBuilder.create("https://myurl.com");
        keyVals.entrySet().stream().forEach((Map.Entry<String, String> e) -> {
            formBuilder.addMultipartFormDataBodyParam(e.getKey(), e.getValue());
        });
        QueryConfiguration form = formBuilder.build();

        Map<String, String> formDataKeyValues =
                form.getBodyQueryParams().stream().collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
        Assertions.assertEquals(keyVals, formDataKeyValues);
        Assertions.assertEquals(BodyFormat.FORM_DATA, form.getBodyType());
    }

    @Test
    public void bodyXFormURLEncodedTest() {
        Map<String, String> keyVals = new HashMap<>();
        keyVals.put("name", "peter");
        keyVals.put("age", "30");
        keyVals.put("company", "Talend");

        final QueryConfigurationBuilder urlEncodedBuilder = QueryConfigurationBuilder.create("https://myurl.com");
        keyVals.entrySet().stream().forEach((Map.Entry<String, String> e) -> {
            urlEncodedBuilder.addXWWWFormURLEncodedBodyParam(e.getKey(), e.getValue());
        });
        QueryConfiguration urlEncoded = urlEncodedBuilder.build();

        Map<String, String> urlEncodedKeyValues =
                urlEncoded.getBodyQueryParams().stream().collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
        Assertions.assertEquals(keyVals, urlEncodedKeyValues);
        Assertions.assertEquals(BodyFormat.X_WWW_FORM_URLENCODED, urlEncoded.getBodyType());
    }

    @Test
    public void emptyURL() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            QueryConfiguration config = QueryConfigurationBuilder.create("    ")
                    .build();
        });
    }

    @ParameterizedTest
    @CsvSource({ "raw/json",
            "json/raw",
            "raw/xml",
            "xml/raw",
            "xml/json",
            "json/xml",
            "raw/formdata",
            "formdata/raw",
            "raw/urlencoded",
            "urlencoded/raw",
            "formdata/urlencoded",
            "urlencoded/formdata"
    })
    public void changeBodyType(String bodys) {
        String[] split = bodys.split("/");
        IllegalArgumentException txtJson = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            QueryConfigurationBuilder builder = QueryConfigurationBuilder.create("https://myurl.com");

            for (String b : split) {
                if ("raw".equals(b)) {
                    builder.setRawTextBody("");
                } else if ("json".equals(b)) {
                    builder.setJSONBody("");
                } else if ("xml".equals(b)) {
                    builder.setXMLBody("");
                } else if ("formdata".equals(b)) {
                    builder.addMultipartFormDataBodyParam("name", "peter");
                } else if ("urlencoded".equals(b)) {
                    builder.addXWWWFormURLEncodedBodyParam("name", "peter");
                } else {
                    throw new RuntimeException(String.format("Unknown body type: %s", b));
                }

            }

            builder.build();
        });
    }

    @Test
    public void buildWithSubstitutionTest() {
        Map<String, String> dictionary = new HashMap<>();
        dictionary.put("/api/version", "v1");
        dictionary.put("/api/endpoint", "getEntity");
        dictionary.put("/user/name", "peter");
        dictionary.put("/user/age", "33");
        dictionary.put("/user/id", "09876");
        dictionary.put("/config/log", "true");
        dictionary.put("/token", "123456");
        dictionary.put("/verb", "HEAD");

        Substitutor.PlaceholderConfiguration placeholderConfiguration =
                new Substitutor.PlaceholderConfiguration("{/input", "}");
        Substitutor substitutor = new Substitutor(placeholderConfiguration, new MapDictionary(dictionary));

        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com/{api}/{endpoint}")
                .setMethod("{/input/verb}")
                .addPathParam("api", "api/{/input/api/version}")
                .addPathParam("endpoint", "{/input/api/endpoint}")
                .addHeader(HttpHeaderHelper.AUTHORIZATION, "Bearer {/input/token}")
                .addXWWWFormURLEncodedBodyParam("name", "Its name is {/input/user/name}.")
                .addXWWWFormURLEncodedBodyParam("age", "He is {/input/user/age}.")
                .addQueryParam("id", "{/input/user/id}")
                .addQueryParam("log", "{/input/config/log}")
                .build(substitutor);

        Assertions.assertEquals("HEAD", config.getMethod());
        Assertions.assertEquals("https://myurl.com/api/v1/getEntity", config.getUrl());
        Assertions.assertEquals("Bearer 123456", config.getHeaders().get(0).getValue());
        Assertions.assertEquals("Its name is peter.", config.getBodyQueryParams().get(0).getValue());
        Assertions.assertEquals("He is 33.", config.getBodyQueryParams().get(1).getValue());
        Assertions.assertEquals("09876", config.getQueryParams().get(0).getValue());
        Assertions.assertEquals("true", config.getQueryParams().get(1).getValue());
    }

    @Test
    public void buildWithSubstitutionBodyTest() {
        Map<String, String> dictionary = new HashMap<>();
        dictionary.put("/user/name", "peter");
        dictionary.put("/user/age", "33");
        dictionary.put("/user/id", "09876");

        Substitutor.PlaceholderConfiguration placeholderConfiguration =
                new Substitutor.PlaceholderConfiguration("{/input", "}");
        Substitutor substitutor = new Substitutor(placeholderConfiguration, new MapDictionary(dictionary));

        String json = "{\n" +
                "\t\"id\": \"{/input/user/id}\",\n" +
                "\t\"name\": \"{/input/user/name}\",\n" +
                "\t\"age\": {/input/user/age}\n" +
                "}";

        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com/")
                .setJSONBody(json)
                .build(substitutor);

        String expected = "{\n" +
                "\t\"id\": \"09876\",\n" +
                "\t\"name\": \"peter\",\n" +
                "\t\"age\": 33\n" +
                "}";
        Assertions.assertEquals(expected, config.getPlainTextBody());
    }

    @Test
    public void setAuthenticationTest() {
        String myLogin = "mylogin";
        String myPassword = "mypassword";
        QueryConfiguration basic = QueryConfigurationBuilder.create("https://myurl.com")
                .setBasicAuthentication(myLogin, myPassword)
                .build();

        Assertions.assertEquals(AuthenticationType.Basic, basic.getAuthenticationType());
        Assertions.assertEquals(myLogin, basic.getLoginPassword().getLogin());
        Assertions.assertEquals(myPassword, basic.getLoginPassword().getPassword());

        QueryConfiguration digest = QueryConfigurationBuilder.create("https://myurl.com")
                .setDigestAuthentication(myLogin, myPassword)
                .build();

        Assertions.assertEquals(AuthenticationType.Digest, digest.getAuthenticationType());
        Assertions.assertEquals(myLogin, digest.getLoginPassword().getLogin());
        Assertions.assertEquals(myPassword, digest.getLoginPassword().getPassword());

        String prefix = "Bearer";
        String token = "1234567890abcDEF";
        QueryConfiguration authToken = QueryConfigurationBuilder.create("https://myurl.com")
                .setAuthorizationToken(prefix, token)
                .build();

        Assertions.assertEquals(AuthenticationType.Authorization_Token, authToken.getAuthenticationType());
        Assertions.assertEquals(String.format("%s %s", prefix, token), authToken.getAuthorizationToken());

        QueryConfiguration authTokenNoPrefix = QueryConfigurationBuilder.create("https://myurl.com")
                .setAuthorizationToken(token)
                .build();

        Assertions.assertEquals(AuthenticationType.Authorization_Token, authTokenNoPrefix.getAuthenticationType());
        Assertions.assertEquals(token, authTokenNoPrefix.getAuthorizationToken());

        QueryConfiguration none = QueryConfigurationBuilder.create("https://myurl.com")
                .setDigestAuthentication(myLogin, myPassword)
                .setNoAuthentication()
                .build();

        Assertions.assertEquals(AuthenticationType.None, none.getAuthenticationType());
        Assertions.assertEquals(null, none.getLoginPassword());
    }

    @ParameterizedTest
    @CsvSource({ "true", "false" })
    public void setAuthenticationTest(boolean bypass) {
        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com")
                .bypassCertificateConfiguration(bypass)
                .build();

        Assertions.assertEquals(bypass, config.isBypassCertificateValidation());
    }

    @ParameterizedTest
    @CsvSource({ "QUERY_PARAMETERS,Authorization,Bearer,123456,Bearer 123456",
            "HEADERS,Authorization,Bearer,123456,Bearer 123456",
            "QUERY_PARAMETERS,'  azerty  ','  prefix  ','  123456  ','prefix 123456'",
            "HEADERS,'  azerty  ','  prefix  ','  123456  ','prefix 123456'",
    })
    public void setAPIKey(String destStr, String name, String prefix, String token, String expectedValue) {
        APIKeyDestination destination = APIKeyDestination.valueOf(destStr);
        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com")
                .setAPIKey(destination, name, prefix, token)
                .build();

        Stream<KeyValuePair> stream;

        if (APIKeyDestination.QUERY_PARAMETERS == destination) {
            stream = config.getQueryParams().stream();
        } else {
            stream = config.getHeaders().stream();
        }

        Optional<KeyValuePair> parameter = stream.filter(kvp -> kvp.getKey().equals(name.trim())).findFirst();

        Assertions.assertTrue(parameter.isPresent());
        Assertions.assertEquals(expectedValue, parameter.get().getValue());

    }

    @ParameterizedTest
    @CsvSource({ "FORM,aaa/zzz/eee",
            "FORM,''",
            "BASIC,qqq",
            "DIGEST,''" })
    public void setOAuth20ClientCredentials(String mode, String scopes) {
        List<String> scopeList = Arrays.asList(scopes.split("/"));
        String endpoint = "https://mydomain.com/oauth/token";
        String clientId = "the_client_id";
        String clientSecret = "the_client_secret";

        QueryConfiguration config = QueryConfigurationBuilder.create("https://mysite/resources/")
                .bypassCertificateConfiguration(true)
                .setMaxNumberOfAcceptedRedirectionsOnSameURI(10)
                .setAllowedURIRedirection("https://uri")
                .acceptRelativeURLRedirection(false)
                .acceptOnlySameHostRedirection(true)
                .setOAuth20ClientCredential(OAuth20.AuthentMode.valueOf(mode), endpoint, clientId, clientSecret,
                        scopeList.stream()
                                .map(s -> new KeyValuePair(OAuth20.Keys.scope.name(), s))
                                .collect(Collectors.toList()),
                        Collections.emptyList())
                .build();

        Assertions.assertEquals(AuthenticationType.OAuth20_Client_Credential, config.getAuthenticationType());

        QueryConfiguration oauthCall = config.getOauthCall();
        Assertions.assertNotNull(oauthCall);
        Assertions.assertEquals(endpoint, oauthCall.getUrl());

        Map<String, String> form =
                oauthCall.getBodyQueryParams()
                        .stream()
                        .filter(e -> !OAuth20.Keys.scope.name().equals(e.getKey())) // scope parameters are checked
                                                                                    // after
                        .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        Assertions.assertEquals("POST", oauthCall.getMethod());
        Assertions.assertTrue(form.containsKey(OAuth20.Keys.grant_type.name()));
        Assertions.assertEquals(OAuth20.GrantType.client_credentials.name(), form.get(OAuth20.Keys.grant_type.name()));

        if (!scopes.trim().isEmpty()) {
            // check scope parameters
            List<String> scopeValues = oauthCall.getBodyQueryParams()
                    .stream()
                    .filter(e -> OAuth20.Keys.scope.name().equals(e.getKey()))
                    .map(e -> e.getValue())
                    .collect(Collectors.toList());

            Assertions.assertEquals(scopeList.size(), scopeValues.size());

            scopeList.stream().forEach(e -> scopeValues.remove(e));
            Assertions.assertEquals(0, scopeValues.size());
        }

        OAuth20.AuthentMode m = OAuth20.AuthentMode.valueOf(mode);
        switch (m) {
        case FORM:
            Assertions.assertEquals(clientId, form.get(OAuth20.Keys.client_id.name()));
            Assertions.assertEquals(clientSecret, form.get(OAuth20.Keys.client_secret.name()));
            break;
        case BASIC:
        case DIGEST:
            Assertions.assertEquals(clientId, oauthCall.getLoginPassword().getLogin());
            Assertions.assertEquals(clientSecret, oauthCall.getLoginPassword().getPassword());
            break;
        }

        // Alignment with main query
        Assertions.assertEquals(config.isBypassCertificateValidation(), oauthCall.isBypassCertificateValidation());
        Assertions.assertEquals(config.isAcceptRedirections(), oauthCall.isAcceptRedirections());
        Assertions.assertEquals(config.getMaxNumberOfAcceptedRedirectionsOnSameURI(),
                oauthCall.getMaxNumberOfAcceptedRedirectionsOnSameURI());
        Assertions.assertEquals(config.getAllowedURIRedirection(), oauthCall.getAllowedURIRedirection());
        Assertions.assertEquals(config.isAcceptRelativeURLRedirection(), oauthCall.isAcceptRelativeURLRedirection());
        Assertions.assertEquals(config.isAcceptOnlySameHostRedirection(), oauthCall.isAcceptOnlySameHostRedirection());
    }

    @ParameterizedTest
    @EnumSource(ResponseFormat.class)
    public void setResponseFormat(ResponseFormat f) {
        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com")
                .setResponseFormat(f)
                .build();

        Assertions.assertEquals(f, config.getResponseFormat());
    }

    @Test
    public void setProxyTest() {
        ProxyConfiguration.ProxyType type = ProxyConfiguration.ProxyType.HTTP;
        String host = "myproxy";
        int port = 3128;
        String login = "  login  ";
        String password = "   password  ";
        QueryConfiguration config = QueryConfigurationBuilder.create("https://myurl.com")
                .setProxy(type, host, port, login, password)
                .build();

        Assertions.assertEquals(type, config.getProxy().getType());
        Assertions.assertEquals(host, config.getProxy().getHost());
        Assertions.assertEquals(port, config.getProxy().getPort());
        Assertions.assertEquals(login.trim(), config.getProxy().getCredentials().getLogin());
        Assertions.assertEquals(password, config.getProxy().getCredentials().getPassword());
    }

    @Test
    public void addAttachmentsTest() {
        Attachment fakeAttachment = new Attachment("someMediaType", new Object());
        QueryConfiguration configuration = QueryConfigurationBuilder.create("https://myurl.com")
                .addAttachment(fakeAttachment)
                .build();

        Assertions.assertEquals(1, configuration.getAttachments().size());
        Assertions.assertEquals(fakeAttachment, configuration.getAttachments().get(0));
        Assertions.assertEquals(BodyFormat.FORM_DATA.getContentType(), configuration.getBodyType().getContentType());
    }

}
