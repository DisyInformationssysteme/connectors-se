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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.talend.components.common.httpclient.api.BodyFormat;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.HTTPMethod;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.http.configuration.Format;
import org.talend.components.http.configuration.Header;
import org.talend.components.http.configuration.Param;
import org.talend.components.http.configuration.RequestBody;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.auth.APIKey;
import org.talend.components.http.configuration.auth.Authentication;
import org.talend.components.http.configuration.auth.Authorization;
import org.talend.components.http.configuration.auth.Basic;
import org.talend.components.http.service.httpClient.HTTPClientService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.junit.ComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Testcontainers
@WithComponents(value = "org.talend.components.http")
public class ClientTestWithHttpbinTest {

    private final static String HTTPBIN_BASE_PROPERTY = "org.talend.components.rest.httpbin_base";

    private final static String HTTPBIN_BASE_PROPERTY_VALUE = System.getProperty(HTTPBIN_BASE_PROPERTY);

    private static GenericContainer<?> httpbin;

    public static Supplier<String> HTTPBIN_BASE;

    private final static int CONNECT_TIMEOUT = 30000;

    private final static int READ_TIMEOUT = 30000;

    @Service
    HTTPClientService service;

    @Service
    RecordBuilderService recordBuilderService;

    @Injected
    private ComponentsHandler handler;

    private RequestConfig config;

    private boolean followRedirects_backup;

    @BeforeAll
    static void startHttpBinContainer() {
        final String dockerImageName = "kennethreitz/httpbin";
        if (HTTPBIN_BASE_PROPERTY_VALUE == null) {
            httpbin = new GenericContainer<>(dockerImageName).withExposedPorts(80).waitingFor(Wait.forHttp("/"));
            httpbin.start();
        }

        HTTPBIN_BASE = () -> {
            String url;
            if (HTTPBIN_BASE_PROPERTY_VALUE != null) {
                url = HTTPBIN_BASE_PROPERTY_VALUE;
            } else {
                final Integer mappedPort = httpbin.getMappedPort(80);
                log.info(String.format("Dockerized httpbin '%s' mapped port is '%s'", dockerImageName, mappedPort));
                url = "http://" + httpbin.getHost() + ":" + mappedPort;
            }
            return url;
        };

    }

    @AfterAll
    static void stopHttpBinContainer() {
        if (httpbin != null && httpbin.isRunning()) {
            httpbin.stop();
        }
    }

    @BeforeEach
    void before() {
        followRedirects_backup = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);

        config = RequestConfigBuilderTest.getEmptyRequestConfig();

        config.getDataset().getDatastore().setBase(HTTPBIN_BASE.get());
        config.getDataset().getDatastore().setConnectionTimeout(CONNECT_TIMEOUT);
        config.getDataset().getDatastore().setReceiveTimeout(READ_TIMEOUT);
    }

    @AfterEach
    void after() {
        HttpURLConnection.setFollowRedirects(followRedirects_backup);
    }

    @Test
    void httpbinGet() throws MalformedURLException, HTTPClientException {
        config.getDataset().setResource("get");
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setFormat(Format.RAW_TEXT);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();

        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));

        final String body = resp.getString("body");
        URL base = new URL(HTTPBIN_BASE.get());
        assertTrue(body.contains(
                service.buildUrl(config.getDataset().getDatastore().getBase(), config.getDataset().getResource())));

        int port = base.getPort();
        assertTrue(body.contains(base.getHost() + (port > 0 ? ":" + port : "")));
    }

    @Test
    void httpbinGetJSONUsingJSONPointer() throws HTTPClientException {
        config.getDataset().setResource("json");
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setFormat(Format.JSON);
        config.getDataset().setSelector("/slideshow/author"); // querying arrays does not work well in stream-json

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();

        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));

        final Record body = resp.getRecord("body");
        Assertions.assertNotNull(body);
        Assertions.assertEquals("Yours Truly", body.getString("field"));
    }

    /**
     * If there are some parameters set, if false is given to setHasXxxx those parameters should not be passed.
     *
     * @throws Exception
     */
    @Test
    void testParamsDisabled() throws MalformedURLException, HTTPClientException {
        config.getDataset().setResource("get");
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setFormat(Format.RAW_TEXT);

        List<Param> queryParams = new ArrayList<>();
        queryParams.add(new Param("params1", "value1"));
        config.getDataset().setHasQueryParams(false);
        config.getDataset().setQueryParams(queryParams);

        List<Header> headerParams = new ArrayList<>();
        headerParams.add(new Header("Header1", "simple value", Header.HEADER_QUERY_DESTINATION.MAIN));
        config.getDataset().setHasHeaders(false);
        config.getDataset().setHeaders(headerParams);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));

        assertFalse(resp.getString("body").contains("params1"));
        assertFalse(resp.getString("body").contains("params2"));

        URL base = new URL(HTTPBIN_BASE.get());
        int port = base.getPort();

        assertTrue(resp.getString("body").contains(base.getHost() + (port > 0 ? ":" + port : "")));
    }

    @Test
    void testQueryAndHeaderParams() throws HTTPClientException {
        String[] verbs =
                { HTTPMethod.DELETE.name(), HTTPMethod.GET.name(), HTTPMethod.POST.name(), HTTPMethod.PUT.name() };
        for (String m : verbs) {
            config.getDataset().setResource(m.toLowerCase());
            config.getDataset().setMethodType(m);
            config.getDataset().setFormat(Format.RAW_TEXT);

            List<Param> queryParams = new ArrayList<>();
            queryParams.add(new Param("params1", "value1"));
            queryParams.add(new Param("params2", "<name>Dupont & Dupond</name>"));
            config.getDataset().setHasQueryParams(true);
            config.getDataset().setQueryParams(queryParams);

            List<Header> headerParams = new ArrayList<>();
            headerParams.add(new Header("Header1", "simple value", Header.HEADER_QUERY_DESTINATION.MAIN));
            headerParams.add(
                    new Header("Header2", "<name>header Dupont & Dupond</name>", Header.HEADER_QUERY_DESTINATION.MAIN));
            config.getDataset().setHasHeaders(true);
            config.getDataset().setHeaders(headerParams);

            QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
            final Iterator<Record> respIt = recordBuilderService
                    .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

            final Record resp = respIt.next();
            assertFalse(respIt.hasNext());

            assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));

            assertTrue(resp.getString("body").contains("value1"));
            assertTrue(resp.getString("body").contains("<name>Dupont & Dupond</name>"));
            assertTrue(resp.getString("body").contains("simple value"));
            assertTrue(resp.getString("body").contains("<name>header Dupont & Dupond</name>"));
        }
    }

    @Test
    void testBasicAuth() throws HTTPClientException {
        String user = "my_user";
        String pwd = "my_password";

        Basic basic = new Basic();
        basic.setUsername(user);
        basic.setPassword(pwd);

        Authentication auth = new Authentication();
        auth.setType(Authorization.AuthorizationType.Basic);
        auth.setBasic(basic);

        config.getDataset().getDatastore().setAuthentication(auth);
        config.getDataset().setMethodType(HTTPMethod.GET.name());

        config.getDataset().setResource("/basic-auth/" + user + "/wrong_" + pwd);
        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respForbiddenIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record respForbidden = respForbiddenIt.next();
        assertFalse(respForbiddenIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, respForbidden.getInt("status"));

        config.getDataset().setResource("/basic-auth/" + user + "/" + pwd);
        queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respOkIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record respOk = respOkIt.next();
        assertFalse(respOkIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, respOk.getInt("status"));
    }

    @Test
    void testBearerAuth() throws HTTPClientException {
        config.getDataset().getDatastore().setBase(HTTPBIN_BASE.get());
        Authentication auth = new Authentication();
        auth.setType(Authorization.AuthorizationType.NoAuth);
        config.getDataset().getDatastore().setAuthentication(auth);
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setResource("/bearer");

        // auth.setBearerToken("");
        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respKoIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record respKo = respKoIt.next();
        assertFalse(respKoIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, respKo.getInt("status"));

        auth = new Authentication();
        auth.setType(Authorization.AuthorizationType.Bearer);
        config.getDataset().getDatastore().setAuthentication(auth);

        auth.setBearerToken("token-123456789");
        queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respOkIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record respOk = respOkIt.next();
        assertFalse(respOkIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, respOk.getInt("status"));
    }

    @ParameterizedTest
    // TODO https://issues.apache.org/jira/browse/CXF-8752
    // @CsvSource(value = { "GET", "POST", "PUT" })
    @CsvSource(value = { "GET" })
    void testRedirect(final String method) throws HTTPClientException {
        String redirect_url = HTTPBIN_BASE.get() + "/" + method.toLowerCase() + "?redirect=ok";
        config.getDataset().setAcceptRedirections(true);
        config.getDataset().setResource("redirect-to?url=" + redirect_url);
        config.getDataset().setMethodType(method);
        config.getDataset().setMaxRedirectOnSameURL(1);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));
    }

    @ParameterizedTest
    @CsvSource(value = { "GET,x", "GET,https://www.google.com" })
    void testRedirectOnlySameHost(final String method, final String redirect_url)
            throws URISyntaxException, HTTPClientException {

        URI uri = new URI(HTTPBIN_BASE.get());
        int port = uri.getPort();
        String mainHost = uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "") + "/get";

        config.getDataset().setResource("redirect-to");
        config.getDataset().setHasQueryParams(true);
        config.getDataset()
                .getQueryParams()
                .add(new Param("url", ("x".equals(redirect_url) ? mainHost : redirect_url)));
        config.getDataset().setMethodType(method);
        config.getDataset().setAcceptRedirections(true);
        config.getDataset().setOnlySameHost(true);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        if ("x".equals(redirect_url)) {
            final Iterator<Record> respIt = recordBuilderService
                    .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
            final Record resp = respIt.next();
            assertFalse(respIt.hasNext());
            assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));
        } else {
            assertThrows(HTTPClientException.class,
                    () -> recordBuilderService
                            .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config));
        }
    }

    @ParameterizedTest
    @CsvSource(value = { "6,-1", "3,3", "3,5" })
    void testRedirectNOk(final int nbRedirect, final int maxRedict) throws HTTPClientException {
        config.getDataset().setResource("redirect/" + nbRedirect);
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setMaxRedirectOnSameURL(maxRedict);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));
    }

    @Disabled("TODO: CXF digest authent doesn't support QOP for now https://jira.talendforge.org/browse/TDI-48347")
    @ParameterizedTest
    @CsvSource(value = { "auth-int,MD5",
            "auth,MD5",
            "auth-int,MD5-sess",
            "auth,MD5-sess",
            "auth-int,SHA-256",
            "auth,SHA-256",
            "auth-int,SHA-512",
            "auth,SHA-512" })
    void testDisgestAuth(final String qop, final String algo) throws HTTPClientException {
        String user = "my_user";
        String pwd = "my_password";

        Basic basic = new Basic();
        basic.setUsername(user);
        basic.setPassword(pwd);

        Authentication auth = new Authentication();
        auth.setType(Authorization.AuthorizationType.Digest);
        auth.setBasic(basic);

        testDigestAuthWithQop(HttpURLConnection.HTTP_OK, user, pwd, auth, qop);
        testDigestAuthWithQop(HttpURLConnection.HTTP_UNAUTHORIZED, user, pwd + "x", auth, qop);

        testDigestAuthWithQopAlgo(HttpURLConnection.HTTP_OK, user, pwd, auth, qop, algo);
        testDigestAuthWithQopAlgo(HttpURLConnection.HTTP_UNAUTHORIZED, user, pwd + "x", auth, qop, algo);

    }

    private void testDigestAuthWithQop(final int expected, final String user, final String pwd,
            final Authentication auth,
            final String qop) throws HTTPClientException {
        config.getDataset().getDatastore().setAuthentication(auth);
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setResource("digest-auth/" + qop + "/" + user + "/" + pwd);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(expected, resp.getInt("status"));
    }

    private void testDigestAuthWithQopAlgo(final int expected, final String user, final String pwd,
            final Authentication auth,
            final String qop, final String algo) throws HTTPClientException {
        config.getDataset().getDatastore().setAuthentication(auth);
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setResource("digest-auth/" + qop + "/" + user + "/" + pwd + "/" + algo);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(expected, resp.getInt("status"));
    }

    @ParameterizedTest
    @CsvSource(value = { "json_notparsed", "xml", "html" })
    void testformats(final String type) throws HTTPClientException {
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setResource(type.endsWith("_notparsed") ? type.substring(0, type.length() - 10) : type);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);
        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());
        assertEquals(HttpURLConnection.HTTP_OK, resp.getInt("status"));

        switch (type) {
        case "json_notparsed": {
            final String body = resp.getString("body");
            final String expected = "{\n" + "  \"slideshow\": {\n" + "    \"author\": \"Yours Truly\", \n"
                    + "    \"date\": \"date of publication\", \n" + "    \"slides\": [\n" + "      {\n"
                    + "        \"title\": \"Wake up to WonderWidgets!\", \n" + "        \"type\": \"all\"\n"
                    + "      }, \n"
                    + "      {\n" + "        \"items\": [\n" + "          \"Why <em>WonderWidgets</em> are great\", \n"
                    + "          \"Who <em>buys</em> WonderWidgets\"\n" + "        ], \n"
                    + "        \"title\": \"Overview\", \n"
                    + "        \"type\": \"all\"\n" + "      }\n" + "    ], \n"
                    + "    \"title\": \"Sample Slide Show\"\n"
                    + "  }\n" + "}\n";
            assertEquals(expected, body);
            break;
        }
        case "xml": {
            final String body = resp.getString("body");
            final String expected = "<?xml version='1.0' encoding='us-ascii'?>\n" + "\n"
                    + "<!--  A SAMPLE set of slides  -->\n"
                    + "\n" + "<slideshow \n" + "    title=\"Sample Slide Show\"\n"
                    + "    date=\"Date of publication\"\n"
                    + "    author=\"Yours Truly\"\n" + "    >\n" + "\n" + "    <!-- TITLE SLIDE -->\n"
                    + "    <slide type=\"all\">\n" + "      <title>Wake up to WonderWidgets!</title>\n"
                    + "    </slide>\n" + "\n"
                    + "    <!-- OVERVIEW -->\n" + "    <slide type=\"all\">\n" + "        <title>Overview</title>\n"
                    + "        <item>Why <em>WonderWidgets</em> are great</item>\n" + "        <item/>\n"
                    + "        <item>Who <em>buys</em> WonderWidgets</item>\n" + "    </slide>\n" + "\n"
                    + "</slideshow>";
            assertEquals(expected, body);
            break;
        }
        case "html": {
            final String body = resp.getString("body");
            final String expected = "<!DOCTYPE html>\n" + "<html>\n" + "  <head>\n" + "  </head>\n" + "  <body>\n"
                    + "      <h1>Herman Melville - Moby-Dick</h1>\n" + "\n" + "      <div>\n" + "        <p>\n"
                    + "          Availing himself of the mild, summer-cool weather that now reigned in these latitudes, and in preparation for the peculiarly active pursuits shortly to be anticipated, Perth, the begrimed, blistered old blacksmith, had not removed his portable forge to the hold again, after concluding his contributory work for Ahab's leg, but still retained it on deck, fast lashed to ringbolts by the foremast; being now almost incessantly invoked by the headsmen, and harpooneers, and bowsmen to do some little job for them; altering, or repairing, or new shaping their various weapons and boat furniture. Often he would be surrounded by an eager circle, all waiting to be served; holding boat-spades, pike-heads, harpoons, and lances, and jealously watching his every sooty movement, as he toiled. Nevertheless, this old man's was a patient hammer wielded by a patient arm. No murmur, no impatience, no petulance did come from him. Silent, slow, and solemn; bowing over still further his chronically broken back, he toiled away, as if toil were life itself, and the heavy beating of his hammer the heavy beating of his heart. And so it was.—Most miserable! A peculiar walk in this old man, a certain slight but painful appearing yawing in his gait, had at an early period of the voyage excited the curiosity of the mariners. And to the importunity of their persisted questionings he had finally given in; and so it came to pass that every one now knew the shameful story of his wretched fate. Belated, and not innocently, one bitter winter's midnight, on the road running between two country towns, the blacksmith half-stupidly felt the deadly numbness stealing over him, and sought refuge in a leaning, dilapidated barn. The issue was, the loss of the extremities of both feet. Out of this revelation, part by part, at last came out the four acts of the gladness, and the one long, and as yet uncatastrophied fifth act of the grief of his life's drama. He was an old man, who, at the age of nearly sixty, had postponedly encountered that thing in sorrow's technicals called ruin. He had been an artisan of famed excellence, and with plenty to do; owned a house and garden; embraced a youthful, daughter-like, loving wife, and three blithe, ruddy children; every Sunday went to a cheerful-looking church, planted in a grove. But one night, under cover of darkness, and further concealed in a most cunning disguisement, a desperate burglar slid into his happy home, and robbed them all of everything. And darker yet to tell, the blacksmith himself did ignorantly conduct this burglar into his family's heart. It was the Bottle Conjuror! Upon the opening of that fatal cork, forth flew the fiend, and shrivelled up his home. Now, for prudent, most wise, and economic reasons, the blacksmith's shop was in the basement of his dwelling, but with a separate entrance to it; so that always had the young and loving healthy wife listened with no unhappy nervousness, but with vigorous pleasure, to the stout ringing of her young-armed old husband's hammer; whose reverberations, muffled by passing through the floors and walls, came up to her, not unsweetly, in her nursery; and so, to stout Labor's iron lullaby, the blacksmith's infants were rocked to slumber. Oh, woe on woe! Oh, Death, why canst thou not sometimes be timely? Hadst thou taken this old blacksmith to thyself ere his full ruin came upon him, then had the young widow had a delicious grief, and her orphans a truly venerable, legendary sire to dream of in their after years; and all of them a care-killing competency.\n"
                    + "        </p>\n" + "      </div>\n" + "  </body>\n" + "</html>";
            assertEquals(expected, body);
            break;
        }
        }
    }

    @Test
    void testBodyFormData() throws HTTPClientException {
        config.getDataset().setHasBody(true);

        RequestBody body = new RequestBody();
        body.setType(BodyFormat.FORM_DATA);
        body.setParams(Arrays.asList(new Param("form_data_1", "<000 001"), new Param("form_data_2", "<000 002")));
        config.getDataset().setBody(body);
        config.getDataset().setMethodType(HTTPMethod.POST.name());
        config.getDataset().setResource("post");
        config.getDataset().setFormat(Format.RAW_TEXT);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());

        String responseBody = resp.getString("body");

        assertTrue(responseBody.contains("\"form_data_1\": \"<000 001\""));
        assertTrue(responseBody.contains("\"form_data_2\": \"<000 002\""));

    }

    @Test
    void testBodyXwwwformURLEncoded() throws HTTPClientException {
        config.getDataset().setHasBody(true);

        RequestBody body = new RequestBody();
        body.setType(BodyFormat.X_WWW_FORM_URLENCODED);
        body.setParams(Arrays.asList(new Param("form_data_1", "<000 001"), new Param("form_data_2", "<000 002")));
        config.getDataset().setBody(body);
        config.getDataset().setMethodType(HTTPMethod.POST.name());
        config.getDataset().setResource("post");
        config.getDataset().setFormat(Format.RAW_TEXT);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record resp = respIt.next();
        assertFalse(respIt.hasNext());

        String responseBody = resp.getString("body");

        assertTrue(responseBody.contains("\"form_data_1\": \"<000 001\""));
        assertTrue(responseBody.contains("\"form_data_2\": \"<000 002\""));
    }

    @Test
    void testBuildContextNotSupportedForHTTPSE() throws HTTPClientException {
        String queryParameterValue = "a_value";
        config.getDataset().setResource("get");
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setFormat(Format.RAW_TEXT);
        config.getDataset().setHasQueryParams(true);
        config.getDataset().setQueryParams(Arrays.asList(new Param("a_parameter", queryParameterValue)));
        config.getDataset().setOutputKeyValuePairs(true);
        config.getDataset().getKeyValuePairs().add(new Param("key1", "This is a simple value"));
        config.getDataset().getKeyValuePairs().add(new Param("key2", "This is another simple value"));
        config.getDataset()
                .getKeyValuePairs()
                .add(new Param("key3", "With susbstitute : {.response.body.args.a_parameter}"));
        config.getDataset()
                .getKeyValuePairs()
                .add(new Param("key4", "With susbstitute : {.response.headers.Content_Type}"));

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        Assertions.assertThrows(UnsupportedOperationException.class, respIt::next);

    }

    @ParameterizedTest
    @CsvSource({ "QUERY_PARAMETERS,Authorization,Bearer,123456,123456",
            "HEADERS,Authorization,Bearer,123456,Bearer 123456",
            "QUERY_PARAMETERS,'  Azerty  ','  prefix  ','  123456  ','123456'",
            "HEADERS,'  Azerty  ','  prefix  ','  123456  ','prefix 123456'",
    })
    void testAPIKey(String destStr, String name, String prefix, String token, String expectedValue)
            throws HTTPClientException {
        config.getDataset().setResource("get");
        config.getDataset().setMethodType(HTTPMethod.GET.name());
        config.getDataset().setFormat(Format.RAW_TEXT);

        APIKey apikey = new APIKey();
        Authorization.Destination destination = Authorization.Destination.valueOf(destStr);
        apikey.setDestination(destination);
        if (destStr.equals(Authorization.Destination.HEADERS.name())) {
            apikey.setHeaderName(name);
        } else {
            apikey.setQueryName(name);
        }

        apikey.setPrefix(prefix);
        apikey.setToken(token);

        config.getDataset().getDatastore().getAuthentication().setType(Authorization.AuthorizationType.APIKey);
        config.getDataset().getDatastore().getAuthentication().setApiKey(apikey);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        final Iterator<Record> respIt = recordBuilderService
                .buildFixedRecord(service.invoke(queryConfiguration, config.isDieOnError()), config);

        final Record record = respIt.next();
        assertFalse(respIt.hasNext());

        String bodyString = record.getString("body");
        assertTrue(bodyString.contains(expectedValue));

    }
}
