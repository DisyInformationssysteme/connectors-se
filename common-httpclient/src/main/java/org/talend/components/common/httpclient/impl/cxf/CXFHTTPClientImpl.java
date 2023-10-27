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

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.AttachmentBuilder;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.auth.HttpAuthHeader;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.transports.http.configuration.ProxyServerType;
import org.talend.components.common.httpclient.api.BodyFormat;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.common.httpclient.api.authentication.AuthenticationType;
import org.talend.components.common.httpclient.api.authentication.OAuth20FlowExecution;
import org.talend.components.common.httpclient.api.authentication.Token;
import org.talend.components.common.httpclient.pagination.PaginationStrategy;
import org.talend.components.common.httpclient.pagination.PaginationStrategyFactory;
import org.talend.components.common.service.http.ValidateSites;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CXFHTTPClientImpl implements HTTPClient<WebClient> {

    private static String DEFAULT_METHOD = "GET";

    private QueryConfiguration queryConfiguration;

    private WebClient webClient;

    private Token token;

    public CXFHTTPClientImpl(final QueryConfiguration queryConfiguration) {
        // This is to force the use of the same classloader of this class
        // If not, it uses the cxf tries to load the Bus with the current thread contextclassloader
        // and if it is different, it throws and exception.
        // https://jira.talendforge.org/browse/TDI-48619
        ClassLoader backupThreadContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

            this.queryConfiguration = queryConfiguration;
            String url = configureURL();
            this.webClient = WebClient.create(url);
        } finally {
            Thread.currentThread().setContextClassLoader(backupThreadContextClassLoader);
        }

    }

    @Override
    public WebClient getNestedClient() {
        return this.webClient;
    }

    @Override
    public void setOAuth20Token(Token token) {
        this.token = token;
    }

    @Override
    public HTTPResponse invoke() throws HTTPClientException {
        validateURL();

        manageAuthentication();

        certificateValidation();

        if (queryConfiguration.getBodyType() != null) {
            webClient
                    .type(queryConfiguration.getBodyType().getContentType());
        }

        if (queryConfiguration.getResponseFormat() != null) {
            webClient.header(HttpHeaders.ACCEPT, queryConfiguration.getResponseFormat().getAcceptedType());
        }

        // Set timeouts
        final HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();
        conduit.getClient().setConnectionTimeout(queryConfiguration.getConnectionTimeout());
        conduit.getClient().setReceiveTimeout(queryConfiguration.getReceiveTimeout());

        // Set headers
        queryConfiguration.getHeaders().stream().forEach(h -> webClient.header(h.getKey(), h.getValue()));

        // Set URL query parameters
        queryConfiguration.getQueryParams().stream().forEach(q -> webClient.query(q.getKey(), q.getValue()));

        manageProxy();

        manageRedirections();

        try {
            Response invoke = getResponse();
            PaginationStrategy paginationStrategy =
                    PaginationStrategyFactory.getPaginationStrategy(this.queryConfiguration);
            CXFHTTPResponseImpl cxfhttpResponse = new CXFHTTPResponseImpl(invoke, paginationStrategy);

            log.info(String.format("HTTP Query '%s' : '%s' ", queryConfiguration.getUrl(),
                    cxfhttpResponse.getStatus().getCodeWithReason()));

            if (queryConfiguration.getAuthenticationType() == AuthenticationType.OAuth20_Client_Credential
                    && this.token != null) {
                cxfhttpResponse.setOAuth20Token(this.token);
            }

            return cxfhttpResponse;
        } catch (Exception e) {
            throw manageExceptions(e);
        }
    }

    private void certificateValidation() throws HTTPClientException {
        final HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();

        // skip server truststore validation
        if (queryConfiguration.isBypassCertificateValidation()) {
            // Disabled certificates verification
            TLSClientParameters params = getTlsClientParameters(conduit);
            params.setTrustManagers(new TrustManager[] { new BlindTrustManager() });
            params.setDisableCNCheck(true);
        } else {
            // init TLS by JVM default SSLContext
            // difficult to mix with bypassCertificateValidation function, so here only disable it if that is true
            TLSClientParameters params = getTlsClientParameters(conduit);
            try {
                params.setSslContext(SSLContext.getDefault());
            } catch (Exception e) {
                log.warn("fail to call SSLContext.getDefault() : " + e.getMessage());
            }
        }
    }

    private TLSClientParameters getTlsClientParameters(HTTPConduit conduit) {
        TLSClientParameters params = conduit.getTlsClientParameters();
        if (params == null) {
            params = new TLSClientParameters();
            conduit.setTlsClientParameters(params);
        }
        return params;
    }

    private void validateURL() throws HTTPClientException {
        String url = this.configureURL();
        if (!ValidateSites.isValidSite(url)) {
            try {
                URI uri = new URI(url);
                throw new HTTPClientException(
                        String.format("The given URL '%s://%s' is not acceptable according to configuration.",
                                uri.getScheme(), uri.getHost()));
            } catch (URISyntaxException e) {
                throw new HTTPClientException(e.getMessage(), e);
            }
        }
    }

    private HTTPClientException manageExceptions(final Exception e) {
        String msg;
        if (queryConfiguration.getMaxNumberOfAcceptedRedirectionsOnSameURI() > 0
                && e.getMessage().startsWith("java.io.IOException: Redirect loop detected on Conduit")) {
            msg = "There has been too many HTTP redirection to the same query.";
        } else if (queryConfiguration.isAcceptOnlySameHostRedirection()
                && e.getMessage().contains("Different HTTP Scheme or Host Redirect detected on Conduit")) {
            msg = "HTTP redirection to another host is forbidden.";
        } else if (e.getCause() instanceof SocketTimeoutException) {
            msg = String.format("HTTP timeout: %s", e.getCause().getMessage());
        } else {
            msg = String.format("The HTTPClient call was failing '%s'", e.getMessage());
        }

        return new HTTPClientException(msg, e);

    }

    private void manageProxy() {
        if (queryConfiguration.getProxy() == null) {
            return;
        }

        HTTPConduit httpConduit = WebClient.getConfig(webClient).getHttpConduit();
        httpConduit.getClient().setProxyServer(queryConfiguration.getProxy().getHost());
        httpConduit.getClient().setProxyServerPort(queryConfiguration.getProxy().getPort());

        httpConduit.getClient()
                .setProxyServerType(ProxyServerType.fromValue(queryConfiguration.getProxy().getType().name()));

        String login = queryConfiguration.getProxy().getCredentials().getLogin();
        String password = queryConfiguration.getProxy().getCredentials().getPassword();
        if (login != null && !login.isEmpty()) {
            httpConduit.getProxyAuthorization().setUserName(login);
        }

        if (password != null && !password.isEmpty()) {
            httpConduit.getProxyAuthorization().setPassword(password);
        }
    }

    private void manageAuthentication() throws HTTPClientException {
        HTTPConduit httpConduit = WebClient.getConfig(webClient).getHttpConduit();

        switch (queryConfiguration.getAuthenticationType()) {
        case Basic:
            AuthorizationPolicy basicAuthPolicy = new AuthorizationPolicy();
            basicAuthPolicy.setUserName(queryConfiguration.getLoginPassword().getLogin());
            basicAuthPolicy.setPassword(queryConfiguration.getLoginPassword().getPassword());
            basicAuthPolicy.setAuthorizationType(HttpAuthHeader.AUTH_TYPE_BASIC);
            httpConduit.setAuthorization(basicAuthPolicy);
            break;
        case Digest:
            /**
             * QOP = auth-int is not supported currently by cxf:3.5.2
             */
            AuthorizationPolicy digestAuthPolicy = new AuthorizationPolicy();
            digestAuthPolicy.setUserName(queryConfiguration.getLoginPassword().getLogin());
            digestAuthPolicy.setPassword(queryConfiguration.getLoginPassword().getPassword());
            digestAuthPolicy.setAuthorizationType(HttpAuthHeader.AUTH_TYPE_DIGEST);
            httpConduit.setAuthorization(digestAuthPolicy);
            break;
        case NTLM:
            AuthorizationPolicy authPolicy = new AuthorizationPolicy();
            authPolicy.setAuthorizationType("NTLM");

            // Login should be domain\login
            authPolicy.setUserName(queryConfiguration.getLoginPassword().getLogin());
            authPolicy.setPassword(queryConfiguration.getLoginPassword().getPassword());
            httpConduit.setAuthorization(authPolicy);
            break;
        case Authorization_Token:
            this.setAuthorizationToken(queryConfiguration.getAuthorizationToken());
            break;
        case OAuth20_Client_Credential:
            if (token == null || token.isExpired()) {
                QueryConfiguration oauthCall = queryConfiguration.getOauthCall();
                OAuth20FlowExecution flow = new OAuth20FlowExecution(oauthCall);
                this.token = flow.executeFlow();
                String type = token.getTokenType();
                if (type.length() > 2) {
                    // https://api.box.com/2.0/ was return type as 'bearer'
                    // But expect 'Bearer' in Authorization as define in
                    // OAUTH RFC: https://datatracker.ietf.org/doc/html/rfc6750#autoid-6
                    type = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
                }
                token.setTokenType(type);
            }

            String t = String.format("%s %s", token.getTokenType(), token.getAccessToken());
            this.setAuthorizationToken(t);
            break;
        }

    }

    private void setAuthorizationToken(String token) {
        webClient.header(HttpHeaderHelper.AUTHORIZATION, token);
    }

    private void manageRedirections() {
        HTTPConduit httpConduit = WebClient.getConfig(webClient).getHttpConduit();
        HTTPClientPolicy policy = httpConduit.getClient();

        if (!queryConfiguration.isAcceptRedirections()) {
            policy.setAutoRedirect(false);
            return;
        }
        policy.setAutoRedirect(true);

        // TODO: Need to wait for https://issues.apache.org/jira/browse/CXF-8752
        /*
         * if (queryConfiguration.getAllowedRedictedVerbs() != null) {
         * WebClient.getConfig(webClient)
         * .getRequestContext()
         * .put(CXFConstants.AUTHORIZED_REDIRECTED_HTTP_VERBS, queryConfiguration.getAllowedRedictedVerbs());
         * }
         */

        WebClient.getConfig(webClient)
                .getRequestContext()
                .put(CXFConstants.AUTO_REDIRECT_SAME_HOST_ONLY,
                        Boolean.toString(queryConfiguration.isAcceptOnlySameHostRedirection()));
        WebClient.getConfig(webClient)
                .getRequestContext()
                .put(CXFConstants.AUTO_REDIRECT_ALLOW_REL_URI,
                        Boolean.toString(queryConfiguration.isAcceptRelativeURLRedirection()));

        if (queryConfiguration.getMaxNumberOfAcceptedRedirectionsOnSameURI() > 0) {
            WebClient.getConfig(webClient)
                    .getRequestContext()
                    .put(CXFConstants.AUTO_REDIRECT_MAX_SAME_URI_COUNT,
                            queryConfiguration.getMaxNumberOfAcceptedRedirectionsOnSameURI());
        }

        if (queryConfiguration.getAllowedURIRedirection() != null) {
            WebClient.getConfig(webClient)
                    .getRequestContext()
                    .put(CXFConstants.AUTO_REDIRECT_ALLOWED_URI, queryConfiguration.getAllowedURIRedirection());
        }
    }

    private Response getResponse() {
        Response invoke;

        ClientConfiguration config = WebClient.getConfig(webClient);

        if (queryConfiguration.isDecompressResponsePayload()) {
            config.getInInterceptors().add(new GZIPInInterceptor());
        }

        BodyFormat bodyType = queryConfiguration.getBodyType();
        if (bodyType == BodyFormat.FORM_DATA) {
            invoke = webClient.invoke(getHTTPMethod(), buildMultiPartBody());
        } else if (bodyType == BodyFormat.X_WWW_FORM_URLENCODED) {
            invoke = webClient.invoke(getHTTPMethod(), buildForm());
        } else {
            invoke = webClient.invoke(getHTTPMethod(), queryConfiguration.getPlainTextBody());
        }

        return invoke;
    }

    private MultipartBody buildMultiPartBody() {
        List<Attachment> attachments = queryConfiguration.getBodyQueryParams().stream().map(p -> {
            AttachmentBuilder attachmentBuilder = new AttachmentBuilder();
            ContentDisposition contentDisposition =
                    new ContentDisposition(String.format("form-data; name=\"%s\"", p.getKey()));
            return attachmentBuilder
                    .id(p.getKey())
                    .contentDisposition(contentDisposition)
                    .object(p.getValue())
                    .build();
        }).collect(Collectors.toList());

        if (queryConfiguration.getAttachments() != null) {
            attachments.addAll(queryConfiguration.getAttachments());
        }

        return new MultipartBody(attachments);
    }

    private Form buildForm() {
        Form form = new Form();
        queryConfiguration.getBodyQueryParams().forEach(kp -> form.param(kp.getKey(), kp.getValue()));
        return form;
    }

    private String getHTTPMethod() {
        Optional<String> method = Optional.ofNullable(this.queryConfiguration.getMethod());
        return method.orElse(DEFAULT_METHOD);
    }

    private String configureURL() {
        return this.queryConfiguration.getUrl();
    }
}
