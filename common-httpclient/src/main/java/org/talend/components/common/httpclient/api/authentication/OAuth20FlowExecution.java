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
package org.talend.components.common.httpclient.api.authentication;

import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;

import lombok.Getter;
import org.talend.components.common.httpclient.api.DefaultConfigurationValues;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.KeyValuePair;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.common.httpclient.factory.HTTPClientFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Execute the OAuth2.0 flow and retrieve the token.
 */
@Slf4j
public class OAuth20FlowExecution {

    @Getter
    private QueryConfiguration config;

    public OAuth20FlowExecution(QueryConfiguration config) {
        this.config = config;
    }

    public Token executeFlow() throws HTTPClientException {
        long current = System.currentTimeMillis();

        HTTPClient httpClient = HTTPClientFactory.create(config);
        HTTPClient.HTTPResponse response = httpClient.invoke();

        String bodyAsString = response.getBodyAsString();
        JsonObject jsonObject;
        try (JsonParser parser = Json.createParser(new StringReader(bodyAsString))) {
            jsonObject = parser.getObject();
        } catch (IllegalStateException e) {
            throw new HTTPClientException(
                    String.format("Can't parse OAuth2.0 token response as a json. Authentication status: %s",
                            response.getStatus().getCodeWithReason()),
                    e);
        }

        // Response 200 > code >= 300 : not successful
        if (!response.isSuccess()) {
            String errorURI = jsonObject.containsKey(OAuth20.errorToken.error_uri.name())
                    ? jsonObject.getString(OAuth20.errorToken.error_uri.name())
                    : "";
            String errorDescription = jsonObject.containsKey(OAuth20.errorToken.error_description.name())
                    ? jsonObject.getString(OAuth20.errorToken.error_description.name())
                    : "";
            String error = jsonObject.containsKey(OAuth20.errorToken.error.name())
                    ? jsonObject.getString(OAuth20.errorToken.error.name())
                    : "";
            throw new HTTPClientException(
                    String.format(
                            "Failing to retrieve OAuth 2.0 token:\nstatus = %s\nerror = %s\ndescription = %s\nuri = %s",
                            response.getStatus().getCodeWithReason(),
                            error,
                            errorDescription,
                            errorURI));
        }

        // Token successfully retrieved
        String accessToken = jsonObject.getString(OAuth20.successToken.access_token.name());
        String tokenType = jsonObject.containsKey(OAuth20.successToken.token_type.name())
                ? jsonObject.getString(OAuth20.successToken.token_type.name())
                : OAuth20.BEARER;
        long expiresIn = jsonObject.containsKey(OAuth20.successToken.expires_in.name())
                ? jsonObject.getJsonNumber(OAuth20.successToken.expires_in.name()).longValue()
                : 0L;

        if (DefaultConfigurationValues.HTTP_CLIENT_OAUTH_TOKEN_FORCED_EXPIRES_IN_VALUE > DefaultConfigurationValues.HTTP_CLIENT_OAUTH_TOKEN_FORCED_EXPIRES_IN_DEFAULT_VALUE) {
            // A value greater that default on is set in
            // DefaultConfigurationValues.HTTP_CLIENT_OAUTH_TOKEN_FORCED_EXPIRES_IN
            // then use it instead of the one retrieve.
            // It allows to refresh token before it is outdated for testing purpose or if system
            // doesn't return a reliable expire_in value.
            // So to always refresh token, you can set
            // System.setProperty(efaultConfigurationValues.HTTP_CLIENT_OAUTH_TOKEN_FORCED_EXPIRES_IN, "-1")
            // It will be always seen as expired for the client.
            expiresIn = DefaultConfigurationValues.HTTP_CLIENT_OAUTH_TOKEN_FORCED_EXPIRES_IN_VALUE;
            log.info(String.format("Force expires_in value for oauth2.0 token to '%s'.", expiresIn));
        }

        // Let some time to do the HTTP call
        long expirationSecurityDuration =
                DefaultConfigurationValues.HTTP_CLIENT_EXPIRESIN_TOKEN_SECURITY_DURATION_VALUE;
        if (expiresIn > expirationSecurityDuration) {
            expiresIn -= expirationSecurityDuration;
        }

        if (accessToken == null) {
            throw new HTTPClientException(String.format("OAuth 2.0 %s response field is null. No token retrieved.",
                    OAuth20.successToken.access_token.name()));
        }

        Token token = new Token(accessToken,
                tokenType,
                current / 1000,
                expiresIn);

        log.info(String.format("New OAuth2.0 token retrieved from '%s'.", this.config.getUrl()));

        return token;
    }

}
