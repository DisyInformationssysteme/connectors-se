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
package org.talend.components.http.service.httpClient;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.talend.components.common.httpclient.api.BodyFormat;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.HTTPMethod;
import org.talend.components.common.httpclient.api.KeyValuePair;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.http.TestUtil;
import org.talend.components.http.configuration.Dataset;
import org.talend.components.http.configuration.Datastore;
import org.talend.components.http.configuration.Header;
import org.talend.components.http.configuration.Param;
import org.talend.components.http.configuration.RequestBody;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.UploadFile;
import org.talend.components.http.configuration.auth.Authentication;
import org.talend.components.http.configuration.auth.Authorization;
import org.talend.components.http.configuration.auth.Basic;
import org.talend.sdk.component.api.exception.ComponentException;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Testcontainers
@WithComponents(value = "org.talend.components.http")
class HTTPClientServiceTest {

    private static GenericContainer<?> httpbin;

    private static Supplier<String> HTTPBIN_BASE;

    private RequestConfig config;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private HTTPClientService service;

    @Injected
    private BaseComponentsHandler handler;

    @BeforeAll
    static void startHttpBinContainer() {
        final String dockerImageName = "kennethreitz/httpbin";
        httpbin = new GenericContainer<>(dockerImageName).withExposedPorts(80).waitingFor(Wait.forHttp("/"));
        httpbin.start();

        HTTPBIN_BASE = () -> {
            final Integer mappedPort = httpbin.getMappedPort(80);
            log.info(String.format("'%s' mapped on port: %s.", dockerImageName, mappedPort));
            final String url = String.format("http://%s:%s", httpbin.getHost(), mappedPort);
            return url;
        };
    }

    @BeforeEach
    public void beforeEach() {
        // Inject needed services
        handler.injectServices(this);

        RequestConfig c = new RequestConfig();
        Dataset dse = new Dataset();
        Datastore dso = new Datastore();
        Authentication auth = new Authentication();
        Basic b = new Basic();
        dso.setAuthentication(auth);
        dse.setDatastore(dso);
        c.setDataset(dse);

        dso.setBase(HTTPBIN_BASE.get());
        dso.setConnectionTimeout(1000);
        dso.setReceiveTimeout(1000);
        auth.setType(Authorization.AuthorizationType.NoAuth);
        dse.setMethodType(HTTPMethod.GET.name());

        this.config = c;
    }

    @ParameterizedTest
    @CsvSource(value = { "200,false", "210,false", "300,true", "401,true", "404,true", "501,true" })
    void testDieOnError(int expectedResponseCode, boolean expectedToFail) throws HTTPClientException {
        config.getDataset().setResource("/status/" + expectedResponseCode);
        config.getDataset().setMethodType("GET");

        config.setDieOnError(true);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        if (expectedToFail) {
            Assertions.assertThrows(ComponentException.class,
                    () -> service.invoke(queryConfiguration, config.isDieOnError()),
                    "Not expected exception result for response " + expectedResponseCode);
        } else {
            HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());
            Assertions.assertEquals(expectedResponseCode, response.getStatus().getCode());
        }
    }

    @ParameterizedTest
    @CsvSource(value = { "get", "post", "patch" })
    public void noAuth(String method) throws HTTPClientException {
        this.config.getDataset().setResource(String.format("/%s", method));
        this.config.getDataset().setMethodType(method.toUpperCase());

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());

        StringReader sr = new StringReader(response.getBodyAsString());
        JsonReader reader = Json.createReader(sr);
        JsonObject jsonObject = reader.readObject();

        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());
        Assertions.assertEquals(
                this.config.getDataset().getDatastore().getBase() + this.config.getDataset().getResource(),
                jsonObject.getString("url"));
    }

    @ParameterizedTest
    @CsvSource(value = { "myLogin,myPassord,myPassord,200",
            "myLogin,myPassord,wrongPassord,401" })
    public void basicAuth(String login, String expectedPwd, String givenPwd, int expectedStatus)
            throws HTTPClientException {
        this.config.getDataset().setResource(String.format("/basic-auth/%s/%s", login, expectedPwd));

        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .setType(Authorization.AuthorizationType.Basic);
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setUsername(login);
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setPassword(givenPwd);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());

        Assertions.assertEquals(expectedStatus, response.getStatus().getCode());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "myLogin,myPassord,auth,MD5,myPassord,200",
            "myLogin,myPassord,auth,MD5,wrongPassord,401",
            "myLogin,myPassord,auth,SHA-256,myPassord,200",
            "myLogin,myPassord,auth,SHA-256,wrongPassord,401",
            "myLogin,myPassord,auth,SHA-512,myPassord,200",
            "myLogin,myPassord,auth,SHA-512,wrongPassord,401"
    /*
     * qop=auth-int is not yet supported by cxf: https://issues.apache.org/jira/browse/CXF-8747
     * ,"myLogin,myPassord,auth-int,MD5,myPassord,200",
     * "myLogin,myPassord,auth-int,MD5,wrongPassord,401",
     * "myLogin,myPassord,auth-int,SHA-256,myPassord,200",
     * "myLogin,myPassord,auth-int,SHA-256,wrongPassord,401",
     * "myLogin,myPassord,auth-int,SHA-512,myPassord,200",
     * "myLogin,myPassord,auth-int,SHA-512,wrongPassord,401"
     */
    })
    public void digestAuth(String login, String expectedPwd, String qop, String algo, String givenPwd,
            int expectedStatus) throws HTTPClientException {
        this.config.getDataset().setResource(String.format("/digest-auth/%s/%s/%s/%s", qop, login, expectedPwd, algo));

        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .setType(Authorization.AuthorizationType.Digest);
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setUsername(login);
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setPassword(givenPwd);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());

        Assertions.assertEquals(expectedStatus, response.getStatus().getCode());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "true,/absolute-redirect/2,3,200",
            "false,/absolute-redirect/2,3,302"
    })
    public void redirectionsOnSameURL(boolean acceptRedirections,
            String resource,
            int maxRedirectionOnSameURL,
            int status) throws HTTPClientException {
        this.config.getDataset().setResource(resource);
        this.config.getDataset().setAcceptRedirections(acceptRedirections);
        this.config.getDataset().setMaxRedirectOnSameURL(maxRedirectionOnSameURL);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());
        Assertions.assertEquals(status, response.getStatus().getCode());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // "false,/redirect-to?url=https%3A%2F%2Fwww.talend.org,true",
            // Only test failing redirection, to not do a real call
            "true,/redirect-to?url=https%3A%2F%2Fwww.talend.org,false"
    })
    public void redirectionsOnSameHost(boolean onlySameHostRedirection,
            String resource,
            boolean success) throws HTTPClientException {
        this.config.getDataset().setResource(resource);
        this.config.getDataset().setAcceptRedirections(true);
        this.config.getDataset().setOnlySameHost(onlySameHostRedirection);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        if (success) {
            HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());
            Assertions.assertEquals(200, response.getStatus());
        } else {
            Assertions.assertThrows(HTTPClientException.class, () -> {
                HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());
            });
        }
    }

    @Disabled
    @Test
    public void bypassCertificateValidation() throws HTTPClientException {
        // TODO : need a HTTPS server with self signed certificate.

        // Manual test done with :
        // https://github.com/Talend/connectors-ee/tree/ypiel/TDI-47889_POC_Improved_REST_Connector/rest-improved/src/main/resources/docker/apache2

        this.config.getDataset().getDatastore().setBase("https://restimprove:45300");
        this.config.getDataset().setResource("digest_authent.json");

        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .setType(Authorization.AuthorizationType.Digest);
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setUsername("john");
        this.config.getDataset()
                .getDatastore()
                .getAuthentication()
                .getBasic()
                .setPassword("abcde");

        this.config.getDataset().getDatastore().setBypassCertificateValidation(true);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);
        HTTPClient.HTTPResponse response = service.invoke(queryConfiguration, config.isDieOnError());

        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus().getCode());
    }

    @Test
    public void testSubstitutionNotSupportedInSE() {
        Record.Builder mainBuilder = this.recordBuilderFactory.newRecordBuilder();
        Record.Builder siteBuilder = this.recordBuilderFactory.newRecordBuilder();
        Record.Builder userBuilder = this.recordBuilderFactory.newRecordBuilder();
        Record.Builder addressBuilder = recordBuilderFactory.newRecordBuilder();
        Record.Builder cityBuilder = recordBuilderFactory.newRecordBuilder();

        Record city = cityBuilder.withInt("zip", 44200)
                .withString("city", "NANTES")
                .build();
        Record address = addressBuilder.withInt("no", 89)
                .withString("street", "Bd de la Prairie au Duc")
                .withRecord("city", city)
                .build();
        Record user = userBuilder
                .withInt("id", 1234)
                .withString("name", "Peter")
                .withInt("age", 35)
                .withBoolean("male", true)
                .withDateTime("birthdate",
                        ZonedDateTime.of(1980, 05, 25, 10, 30, 40, 50, ZoneId.of("Europe/Paris")))
                .withDouble("size", 1.85)
                .withRecord("address", address)
                .build();

        Record site = siteBuilder.withInt("_port", 8080)
                .withString("_scheme", "https")
                .withString("_apivers", "3.0")
                .withString("_endpoint", "user")
                .withString("_format", "json")
                .withString("_key", "123ABCDE")
                .withString("_urlbase", "myurl")
                .build();

        Record main = mainBuilder.withRecord("site", site).withRecord("user", user).build();

        this.config.getDataset().getDatastore().setBase("{protocol}://{.input.site._urlbase}:{port}");
        this.config.getDataset().setResource("api/{version}/{endpoint}");

        this.config.getDataset().setHasPathParams(true);
        this.config.getDataset().getPathParams().add(new Param("protocol", "{.input.site._scheme}"));
        this.config.getDataset().getPathParams().add(new Param("port", "{.input.site._port}"));
        this.config.getDataset().getPathParams().add(new Param("version", "{.input.site._apivers}"));
        this.config.getDataset().getPathParams().add(new Param("endpoint", "{.input.site._endpoint}"));

        this.config.getDataset().setHasQueryParams(true);
        this.config.getDataset().getQueryParams().add(new Param("id", "_{.input.user.id}_"));
        this.config.getDataset().getQueryParams().add(new Param("zip", "(({.input.user.address.city.zip}))"));

        this.config.getDataset().setHasHeaders(true);
        this.config.getDataset()
                .getHeaders()
                .add(new Header("Accept", "application/{.input.site._format}", Header.HEADER_QUERY_DESTINATION.MAIN));
        this.config.getDataset()
                .getHeaders()
                .add(new Header("Authorization", "Baerer {.input.site._key}", Header.HEADER_QUERY_DESTINATION.MAIN));

        this.config.getDataset().setHasBody(true);
        this.config.getDataset().setBody(new RequestBody());
        this.config.getDataset().getBody().setType(BodyFormat.TEXT);
        this.config.getDataset()
                .getBody()
                .setTextContent(
                        "Hello {.input.user.name},\nYou are {.input.user.age} years old. Your birthdate is {.input.user.birthdate}.");

        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> service.convertConfiguration(this.config, main));
    }

    @ParameterizedTest
    @CsvSource(
            value = { "http://www.domain.com,,http://www.domain.com",
                    "http://www.domain.com/,,http://www.domain.com/",
                    "http://www.domain.com,get,http://www.domain.com/get",
                    "http://www.domain.com/,get,http://www.domain.com/get",
                    "http://www.domain.com,/get,http://www.domain.com/get",
                    "   http://www.domain.com/ ,  /get ,http://www.domain.com/get" })
    public void buildUrl(final String base, final String resource, final String expected) {
        String url = HTTPClientService.buildUrl(base, resource);
        Assertions.assertEquals(expected, url);
    }

    @Test
    void testAddAttachments() {
        String expectedAttachmentFileName = "fakeAttachment.txt";
        String expectedAttachmentName = "myAttachment1";
        List<UploadFile> attachmentsToUpload = new ArrayList<>();
        UploadFile uploadFile = new UploadFile();
        uploadFile.setName(expectedAttachmentName);
        uploadFile.setFilePath("src/test/resources/org/talend/components/http/service/" + expectedAttachmentFileName);
        uploadFile.setContentType("text/plain");
        uploadFile.setEncoding("UTF-8");
        attachmentsToUpload.add(uploadFile);
        config.setUploadFiles(true);
        config.setUploadFileTable(attachmentsToUpload);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);

        Assertions.assertEquals(1, queryConfiguration.getAttachments().size());
        Attachment attachment = queryConfiguration.getAttachments().get(0);

        Assertions.assertEquals("text", attachment.getContentType().getType());
        Assertions.assertEquals(expectedAttachmentFileName, attachment.getContentDisposition().getFilename());
        Assertions.assertEquals(expectedAttachmentName, attachment.getContentDisposition().getParameter("file"));
        Assertions.assertEquals(expectedAttachmentName, attachment.getContentId());
        Assertions.assertTrue(attachment.getHeader("Content-Type").contains("UTF-8"));
    }

    @Test
    void testAddAttachmentWithSpecificName() {
        String expectedAttachmentFileName = "你好.txt";
        String expectedAttachmentName = "utf8Attachment";
        List<UploadFile> attachmentsToUpload = new ArrayList<>();
        UploadFile uploadFile = new UploadFile();
        uploadFile.setName(expectedAttachmentName);
        uploadFile.setFilePath("src/test/resources/org/talend/components/http/service/" + expectedAttachmentFileName);
        uploadFile.setContentType("text/plain");
        uploadFile.setEncoding("UTF-8");
        attachmentsToUpload.add(uploadFile);
        config.setUploadFiles(true);
        config.setUploadFileTable(attachmentsToUpload);

        QueryConfiguration queryConfiguration = service.convertConfiguration(config, null);

        Assertions.assertEquals(1, queryConfiguration.getAttachments().size());
        Attachment attachment = queryConfiguration.getAttachments().get(0);

        Assertions.assertEquals("text", attachment.getContentType().getType());
        Assertions.assertEquals(expectedAttachmentFileName, attachment.getContentDisposition().getFilename());
        // assert the filename* value is encoded
        Assertions.assertEquals("filename*=UTF-8''\"%E4%BD%A0%E5%A5%BD.txt\"",
                Arrays.stream(attachment.getHeader("Content-Disposition").split(","))
                        .filter(parameter -> parameter.startsWith("filename*"))
                        .collect(Collectors.joining()));
        // the decoded value is correct
        Assertions.assertEquals(expectedAttachmentFileName,
                attachment.getContentDisposition().getParameter("filename*"));
        Assertions.assertEquals(expectedAttachmentName, attachment.getContentDisposition().getParameter("file"));
        Assertions.assertEquals(expectedAttachmentName, attachment.getContentId());
    }

}
