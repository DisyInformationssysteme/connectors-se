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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.talend.components.common.httpclient.api.authentication.APIKeyDestination;
import org.talend.components.common.httpclient.api.authentication.AuthenticationType;
import org.talend.components.common.httpclient.api.authentication.LoginPassword;
import org.talend.components.common.httpclient.api.authentication.OAuth20;
import org.talend.components.common.httpclient.api.pagination.OffsetLimitPagination;
import org.talend.components.common.httpclient.api.pagination.PaginationParametersLocation;
import org.talend.components.common.httpclient.api.substitutor.MapDictionary;
import org.talend.components.common.httpclient.api.substitutor.Substitutor;
import org.talend.components.common.httpclient.pagination.PaginationStrategy;
import org.talend.components.common.httpclient.pagination.PaginationStrategyFactory;

public class QueryConfigurationBuilder {

    private QueryConfiguration queryConfiguration;

    private QueryConfigurationBuilder oauthCallBuilder;

    private QueryConfigurationBuilder(QueryConfiguration config) {
        this.queryConfiguration = config;
    }

    public static QueryConfigurationBuilder create(String url) {
        url = notEmptyNorNull("http.configuration.URL", url, true);
        QueryConfiguration config = new QueryConfiguration();
        config.setUrl(url);
        return new QueryConfigurationBuilder(config);
    }

    public QueryConfigurationBuilder setConnectionTimeout(long timeout) {
        notNegative("http.configuration.connectionTimeout", timeout);
        queryConfiguration.setConnectionTimeout(timeout);
        return this;
    }

    public QueryConfigurationBuilder bypassCertificateConfiguration(boolean bypass) {
        queryConfiguration.setBypassCertificateValidation(bypass);
        return this;
    }

    public QueryConfigurationBuilder setReceiveTimeout(long timeout) {
        notNegative("http.configuration..receiveTimeout", timeout);
        queryConfiguration.setReceiveTimeout(timeout);
        return this;
    }

    public QueryConfigurationBuilder setMethod(String method) {
        method = notEmptyNorNull("http.configuration.method", method, true);
        queryConfiguration.setMethod(method);

        return this;
    }

    public QueryConfigurationBuilder setNoAuthentication() {
        queryConfiguration.setAuthenticationType(AuthenticationType.None);
        queryConfiguration.setLoginPassword(null);

        return this;
    }

    public QueryConfigurationBuilder setBasicAuthentication(String login, String password) {
        notEmptyNorNull("http.configuration.authentication.basic.login", login, false);
        notEmptyNorNull("http.configuration.authentication.basic.password", password, false);
        LoginPassword lp = new LoginPassword(login, password);
        queryConfiguration.setAuthenticationType(AuthenticationType.Basic);
        queryConfiguration.setLoginPassword(lp);

        return this;
    }

    public QueryConfigurationBuilder setDigestAuthentication(String login, String password) {
        notEmptyNorNull("http.configuration.authentication.digest.login", login, false);
        notEmptyNorNull("http.configuration.authentication.digest.password", password, false);
        LoginPassword lp = new LoginPassword(login, password);
        queryConfiguration.setAuthenticationType(AuthenticationType.Digest);
        queryConfiguration.setLoginPassword(lp);

        return this;
    }

    public QueryConfigurationBuilder setNTLMAuthentication(String login, String password) {
        notEmptyNorNull("http.configuration.authentication.ntlm.login", login, false);
        notEmptyNorNull("http.configuration.authentication.ntlm.password", password, false);
        LoginPassword lp = new LoginPassword(login, password);
        queryConfiguration.setAuthenticationType(AuthenticationType.NTLM);
        queryConfiguration.setLoginPassword(lp);

        return this;
    }

    public QueryConfigurationBuilder setAuthorizationToken(String prefix, String token) {
        notEmptyNorNull("http.configuration.authentication.authorization.token", token, false);
        queryConfiguration.setAuthenticationType(AuthenticationType.Authorization_Token);

        if (prefix != null) {
            prefix = prefix.trim();
            if (!prefix.isEmpty()) {
                token = String.format("%s %s", prefix, token);
            }
        }

        queryConfiguration.setAuthorizationToken(token);

        return this;
    }

    public QueryConfigurationBuilder setAuthorizationToken(String token) {
        return this.setAuthorizationToken(null, token);
    }

    public QueryConfigurationBuilder setOAuth20ClientCredential(OAuth20.AuthentMode mode, String tokenEndpoint,
            String clientId, String clientSecret, List<KeyValuePair> params, List<KeyValuePair> headers) {

        notNull("http.configuration.authentication.oauth.client_credential.authentication.mode", mode);
        tokenEndpoint = notEmptyNorNull("http.configuration.authentication.oauth.client_credential.token_endpoint",
                tokenEndpoint, true);
        clientId =
                notEmptyNorNull("http.configuration.authentication.oauth.client_credential.clientId", clientId, true);
        notNull("http.configuration.authentication.oauth.client_credential.clientSecret", clientSecret);

        QueryConfigurationBuilder oauth20QueryConfigurationBuilder = QueryConfigurationBuilder.create(tokenEndpoint);

        oauth20QueryConfigurationBuilder.addXWWWFormURLEncodedBodyParam(OAuth20.Keys.grant_type.name(),
                OAuth20.GrantType.client_credentials.name());

        params.stream()
                .forEach(kvp -> oauth20QueryConfigurationBuilder.addXWWWFormURLEncodedBodyParam(kvp.getKey(),
                        kvp.getValue()));
        String concatParams =
                params.stream().map(kvp -> kvp.getKey() + "=" + kvp.getValue()).collect(Collectors.joining(";"));

        switch (mode) {
        case FORM:
            oauth20QueryConfigurationBuilder.addXWWWFormURLEncodedBodyParam(OAuth20.Keys.client_id.name(), clientId);
            oauth20QueryConfigurationBuilder.addXWWWFormURLEncodedBodyParam(OAuth20.Keys.client_secret.name(),
                    clientSecret);
            break;
        case BASIC:
            oauth20QueryConfigurationBuilder.setBasicAuthentication(clientId, clientSecret);
            break;
        case DIGEST:
            oauth20QueryConfigurationBuilder.setDigestAuthentication(clientId, clientSecret);
            break;
        }

        oauth20QueryConfigurationBuilder.setMethod("POST");

        headers.stream().forEach(h -> oauth20QueryConfigurationBuilder.addHeader(h.getKey(), h.getValue()));

        queryConfiguration.setAuthenticationType(AuthenticationType.OAuth20_Client_Credential);
        this.setOauthCallBuilder(oauth20QueryConfigurationBuilder);

        try {
            // Build a key that could be used to retrieve existing token
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(tokenEndpoint.getBytes(StandardCharsets.UTF_8));
            md.update(OAuth20.GrantType.client_credentials.name().getBytes(StandardCharsets.UTF_8));
            md.update(concatParams.getBytes(StandardCharsets.UTF_8));
            md.update(clientId.getBytes(StandardCharsets.UTF_8));
            md.update(clientSecret.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            queryConfiguration.setOAuthTokenCacheKey(Optional.ofNullable(new String(digest, StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        return this;
    }

    public QueryConfigurationBuilder addPathParam(String key, String value) {
        key = notEmptyNorNull("http.configuration.pathParameter", key, true);
        this.queryConfiguration.getUrlPathParams().put(key, value);
        return this;
    }

    public QueryConfigurationBuilder addHeader(String key, String value) {
        key = notEmptyNorNull("http.configuration.header", key, true);
        this.queryConfiguration.getHeaders().add(new KeyValuePair(key, value));
        return this;
    }

    public QueryConfigurationBuilder addQueryParam(String key, String value) {
        key = notEmptyNorNull("http.configuration.queryParameter", key, true);
        this.queryConfiguration.getQueryParams().add(new KeyValuePair(key, value));

        return this;
    }

    public QueryConfigurationBuilder setAPIKey(APIKeyDestination destination, String name, String prefix,
            String token) {
        notNull("http.configuration.apikey.destination", destination);
        name = notEmptyNorNull("http.configuration.apikey.name", name, true);
        prefix = prefix == null ? "" : prefix.trim();
        token = token == null ? "" : token.trim();

        token = String.format("%s %s", prefix, token).trim();
        if (APIKeyDestination.QUERY_PARAMETERS == destination) {
            this.addQueryParam(name, token);
        } else {
            // currently only two value, here Authorization.Destination.HEADER
            this.addHeader(name, token);
        }

        return this;
    }

    public QueryConfigurationBuilder setRawTextBody(String content) {
        this.setRawTextBody(content, BodyFormat.TEXT);
        return this;
    }

    public QueryConfigurationBuilder setJSONBody(String content) {
        this.setRawTextBody(content, BodyFormat.JSON);
        return this;
    }

    public QueryConfigurationBuilder setXMLBody(String content) {
        this.setRawTextBody(content, BodyFormat.XML);

        return this;
    }

    private void setRawTextBody(String content, BodyFormat type) {
        checkBodyAlreadySet(type);
        queryConfiguration.setBodyType(type);
        queryConfiguration.setPlainTextBody(changeNullToEmpty(content, false));
    }

    /**
     * Define the body as multipart/form-data, and add a key/value parameter.
     *
     * @param key The key of the parameter
     * @param value The value of the parameter
     * @return The builder
     */
    public QueryConfigurationBuilder addMultipartFormDataBodyParam(String key, String value) {
        addBodyParameter(key, value, BodyFormat.FORM_DATA);
        return this;
    }

    public QueryConfigurationBuilder addAttachment(Attachment att) {
        checkBodyAlreadySet(BodyFormat.FORM_DATA);
        queryConfiguration.getAttachments().add(att);

        queryConfiguration.setBodyType(BodyFormat.FORM_DATA);
        return this;
    }

    public QueryConfigurationBuilder decompressResponsePayload(boolean decompress) {
        this.queryConfiguration.setDecompressResponsePayload(decompress);
        return this;
    }

    /**
     * Define the body as application/x-www-form-urlencoded, and add a key/value parameter.
     *
     * @param key The key of the parameter
     * @param value The value of the parameter
     * @return The builder
     */
    public QueryConfigurationBuilder addXWWWFormURLEncodedBodyParam(String key, String value) {
        addBodyParameter(key, value, BodyFormat.X_WWW_FORM_URLENCODED);
        return this;
    }

    public QueryConfigurationBuilder setMaxNumberOfAcceptedRedirectionsOnSameURI(int n) {
        this.queryConfiguration.setMaxNumberOfAcceptedRedirectionsOnSameURI(n);
        return this;
    }

    public QueryConfigurationBuilder acceptRedirection(boolean accept) {
        this.queryConfiguration.setAcceptRedirections(accept);
        return this;
    }

    public QueryConfigurationBuilder acceptOnlySameHostRedirection(boolean b) {
        this.queryConfiguration.setAcceptOnlySameHostRedirection(b);
        return this;
    }

    public QueryConfigurationBuilder acceptRelativeURLRedirection(boolean b) {
        this.queryConfiguration.setAcceptRelativeURLRedirection(b);
        return this;
    }

    public QueryConfigurationBuilder setAllowedURIRedirection(String allowedURIRedirection) {
        allowedURIRedirection = notEmptyNorNull("allowed redirect URI", allowedURIRedirection, true);
        this.queryConfiguration.setAllowedURIRedirection(allowedURIRedirection);
        return this;
    }

    /**
     * A coma separated HTTP verbs.
     * If not set, default list of the underlying client is used.
     */
    // Need to wait for https://issues.apache.org/jira/browse/CXF-8752
    /*
     * public QueryConfigurationBuilder setAllowedRedirectedVerbs(String allowedRedirectedVerbs) {
     * allowedRedirectedVerbs = notEmptyNorNull("allowed redirected verbs", allowedRedirectedVerbs, true);
     * this.queryConfiguration.setAllowedRedictedVerbs(allowedRedirectedVerbs);
     * return this;
     * }
     */
    public QueryConfigurationBuilder setResponseFormat(ResponseFormat responseFormat) {
        notNull("http.configuration.responseFormat", responseFormat);
        this.queryConfiguration.setResponseFormat(responseFormat);
        return this;
    }

    private void addBodyParameter(String key, String value, BodyFormat type) {
        checkBodyAlreadySet(type);

        key = notEmptyNorNull("http.configuration.body.queryParameter.name", key, true);
        queryConfiguration.setBodyType(type);
        queryConfiguration.getBodyQueryParams().add(new KeyValuePair(key, changeNullToEmpty(value, false)));
    }

    public QueryConfigurationBuilder setHTTPProxy(String host, int port, String login,
            String password) {
        return this.setProxy(ProxyConfiguration.ProxyType.HTTP, host, port, login, password);
    }

    public QueryConfigurationBuilder setSOCKSProxy(String host, int port) {
        return this.setProxy(ProxyConfiguration.ProxyType.SOCKS, host, port, null, null);
    }

    protected QueryConfigurationBuilder setProxy(ProxyConfiguration.ProxyType type, String host, int port, String login,
            String password) {
        host = notEmptyNorNull("http.configuration.proxy.host", host, true);

        if (login != null) {
            login = login.trim();
        }

        notNegative("http.configuration.proxy.port", port);
        queryConfiguration.setProxy(new ProxyConfiguration(type, host, port, login, password));
        return this;
    }

    public QueryConfigurationBuilder setOffsetLimitPagination(PaginationParametersLocation location,
            String offsetParamName, String offsetValue, String limitParamName,
            String limitValue, String elementsPath) {

        OffsetLimitPagination offsetLimitPagination = new OffsetLimitPagination(location, offsetParamName,
                offsetValue, limitParamName, limitValue, elementsPath);

        queryConfiguration.setOffsetLimitPagination(offsetLimitPagination);

        return this;
    }

    private static String changeNullToEmpty(String value, boolean trim) {
        return value == null ? "" : (trim ? value.trim() : value);
    }

    private static void notNull(String property, Object value) {
        if (value != null) {
            return;
        }
        throw new IllegalArgumentException(String.format("The property %s can't receive null as value.", property));
    }

    private static void notNegative(String key, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    String.format("%s value must be a positive value: %s", key, value));
        }
    }

    private void checkBodyAlreadySet(final BodyFormat type) {
        if (queryConfiguration.getBodyType() != null && queryConfiguration.getBodyType() != type) {
            throw new IllegalArgumentException("Body has already been set, you can't change its type.");
        }
    }

    private static String notEmptyNorNull(String property, String value, boolean trim) {
        notNull(property, value);

        value = trim ? value.trim() : value;
        if (!value.isEmpty()) {
            return value;
        }
        throw new IllegalArgumentException(String.format("The property %s can't receive empty value.", property));
    }

    public QueryConfiguration build() {

        initiatePagination();

        substituteUrl();
        finalizeOAuthConfiguration();
        return queryConfiguration;
    }

    public QueryConfiguration build(final Substitutor substitutor) {

        initiatePagination();

        substitute(substitutor);
        Optional<QueryConfigurationBuilder> oauthCallBuilderOptional = this.getOauthCallBuilder();
        if (oauthCallBuilderOptional.isPresent()) {
            oauthCallBuilderOptional.get().substitute(substitutor);
        }
        return build();
    }

    /**
     * Can bee called twice with buidl() & build(Substitutor) but prevented by QueryConfiguration#initPaginationDone.
     */
    private void initiatePagination() {
        if (this.queryConfiguration.isInitPaginationDone()) {
            // Pagination already initiated.
            return;
        }

        PaginationStrategy paginationStrategy =
                PaginationStrategyFactory.getPaginationStrategy(this.queryConfiguration);
        paginationStrategy.initiatePagination(this.queryConfiguration);
    }

    private void setOauthCallBuilder(QueryConfigurationBuilder oauthCallBuilder) {
        this.oauthCallBuilder = oauthCallBuilder;
    }

    private Optional<QueryConfigurationBuilder> getOauthCallBuilder() {
        return Optional.ofNullable(this.oauthCallBuilder);
    }

    /**
     * Align OAuth call, if exists, with the main configuration.
     * OAUTH should be aligned with certificate validation and redirection configuration.
     */
    private void finalizeOAuthConfiguration() {
        if (queryConfiguration.getAuthenticationType() == AuthenticationType.OAuth20_Client_Credential
                && this.getOauthCallBuilder().isPresent()) {

            this.queryConfiguration.setOauthCall(this.getOauthCallBuilder().get().build());

            QueryConfiguration oauthCallConfig = queryConfiguration.getOauthCall();
            oauthCallConfig
                    .setBypassCertificateValidation(queryConfiguration.isBypassCertificateValidation());
            oauthCallConfig
                    .setAcceptRedirections(queryConfiguration.isAcceptRedirections());
            oauthCallConfig
                    .setMaxNumberOfAcceptedRedirectionsOnSameURI(
                            queryConfiguration.getMaxNumberOfAcceptedRedirectionsOnSameURI());
            oauthCallConfig
                    .setAllowedURIRedirection(queryConfiguration.getAllowedURIRedirection());
            oauthCallConfig
                    .setAcceptRelativeURLRedirection(queryConfiguration.isAcceptRelativeURLRedirection());
            oauthCallConfig
                    .setAcceptOnlySameHostRedirection(queryConfiguration.isAcceptOnlySameHostRedirection());
        }
    }

    /**
     * URL substitution is done after all other substition since in pathParameter there could be some value with place
     * holder to be replaced before url.
     * There is no need of external substitutor.
     */
    private void substituteUrl() {
        // Substitute URL
        MapDictionary pathParamsDictionary = new MapDictionary(this.queryConfiguration.getUrlPathParams());
        Substitutor.PlaceholderConfiguration placeholderConfiguration =
                new Substitutor.PlaceholderConfiguration(
                        DefaultConfigurationValues.HTTP_CLIENT_URL_PLACE_HOLDER_BEGIN_VALUE,
                        DefaultConfigurationValues.HTTP_CLIENT_URL_PLACE_HOLDER_END_VALUE);
        Substitutor urlSubstitutor = new Substitutor(placeholderConfiguration, pathParamsDictionary);
        String substitutedURL = urlSubstitutor.replace(this.queryConfiguration.getUrl());
        this.queryConfiguration.setUrl(substitutedURL);
    }

    /**
     * This method will substitute placeholder define in configuration.
     * For instance the url can be "https://mysite.com/api/{version}/user/get" then, "{version}" could be replaced by a
     * concrete value like "1.0".
     * The same in almost all other value.
     * It relies on org.talend.component:common from connectors-se:
     * https://github.com/Talend/connectors-se/blob/master/common/src/main/java/org/talend/components/common/text/Substitutor.java
     *
     * @param substitutor All configuration element can have placeholder according to the given substitur that will
     * replace them with concrete values.
     */
    private void substitute(final Substitutor substitutor) {
        // Single values

        // URL base can have direct substitution, and substitution from Path parameters
        this.queryConfiguration.setUrl(substitutor.replace(this.queryConfiguration.getUrl()));
        this.queryConfiguration.setMethod(substitutor.replace(this.queryConfiguration.getMethod()));

        if (this.queryConfiguration.getLoginPassword() != null) {
            this.queryConfiguration.getLoginPassword()
                    .setLogin(substitutor.replace(this.queryConfiguration.getLoginPassword().getLogin()));
            this.queryConfiguration.getLoginPassword()
                    .setPassword(substitutor.replace(this.queryConfiguration.getLoginPassword().getPassword()));
        }

        this.queryConfiguration
                .setAuthorizationToken(substitutor.replace(this.queryConfiguration.getAuthorizationToken()));
        this.queryConfiguration
                .setAllowedURIRedirection(substitutor.replace(this.queryConfiguration.getAllowedURIRedirection()));

        if (this.queryConfiguration.getProxy() != null) {
            this.queryConfiguration.getProxy()
                    .setHost(substitutor.replace(this.queryConfiguration.getProxy().getHost()));

            if (this.queryConfiguration.getProxy().getCredentials() != null) {
                this.queryConfiguration.getProxy()
                        .getCredentials()
                        .setLogin(substitutor.replace(this.queryConfiguration.getProxy().getCredentials().getLogin()));
                this.queryConfiguration.getProxy()
                        .getCredentials()
                        .setPassword(
                                substitutor.replace(this.queryConfiguration.getProxy().getCredentials().getPassword()));
            }

        }

        // Lists

        // Substitute URL path parameters
        Map<String, String> substitutedURLPathParams = this.queryConfiguration.getUrlPathParams()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(p -> p.getKey(), p -> {
                    return substitutor.replace(p.getValue());
                }));
        this.queryConfiguration.setUrlPathParams(substitutedURLPathParams);

        // Substitute Body query parameters
        List<KeyValuePair> substitutedKVPBody = this.queryConfiguration.getBodyQueryParams().stream().map(p -> {
            return new KeyValuePair(p.getKey(), substitutor.replace(p.getValue()));
        }).collect(Collectors.toList());
        this.queryConfiguration.setBodyQueryParams(substitutedKVPBody);

        // Substitute body plain text
        if (this.queryConfiguration.getPlainTextBody() != null) {
            String substitutedPlainTextBody = substitutor.replace(this.queryConfiguration.getPlainTextBody());
            this.queryConfiguration.setPlainTextBody(substitutedPlainTextBody);
        }

        // Substitute headers
        List<KeyValuePair> substitutedHeaders = this.queryConfiguration.getHeaders().stream().map(p -> {
            return new KeyValuePair(p.getKey(), substitutor.replace(p.getValue()));
        }).collect(Collectors.toList());
        this.queryConfiguration.setHeaders(substitutedHeaders);

        // Substitute query parameters
        List<KeyValuePair> substitutedHQueryParams = this.queryConfiguration.getQueryParams().stream().map(p -> {
            return new KeyValuePair(p.getKey(), substitutor.replace(p.getValue()));
        }).collect(Collectors.toList());
        this.queryConfiguration.setQueryParams(substitutedHQueryParams);
    }
}
