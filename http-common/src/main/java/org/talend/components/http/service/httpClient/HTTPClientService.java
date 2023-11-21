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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.activation.FileDataSource;
import javax.json.stream.JsonParserFactory;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.KeyValuePair;
import org.talend.components.common.httpclient.api.ProxyConfiguration;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.common.httpclient.api.QueryConfigurationBuilder;
import org.talend.components.common.httpclient.api.authentication.APIKeyDestination;
import org.talend.components.common.httpclient.api.authentication.Token;
import org.talend.components.common.httpclient.api.pagination.PaginationParametersLocation;
import org.talend.components.common.httpclient.api.substitutor.Substitutor;
import org.talend.components.common.httpclient.factory.HTTPClientFactory;
import org.talend.components.http.configuration.Header;
import org.talend.components.http.configuration.RequestBody;
import org.talend.components.http.configuration.RequestConfig;
import org.talend.components.http.configuration.UploadFile;
import org.talend.components.http.configuration.auth.APIKey;
import org.talend.components.http.configuration.auth.Authorization;
import org.talend.components.http.configuration.auth.OAuth20;
import org.talend.components.http.configuration.pagination.OffsetLimitStrategyConfig;
import org.talend.components.http.configuration.pagination.Pagination;
import org.talend.components.http.service.ClassLoaderInvokeUtils;
import org.talend.components.http.service.I18n;
import org.talend.components.http.service.RecordBuilderService;
import org.talend.components.http.service.provider.DictionaryProvider;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HTTPClientService {

    @Service
    protected RecordBuilderFactory recordBuilderFactory;

    @Service
    private JsonParserFactory jsonParserFactory;

    @Service
    private I18n messages;

    private Map<String, Token> tokenCache = new HashMap<>();

    public HTTPClient.HTTPResponse invoke(QueryConfiguration queryConfiguration, boolean isDieOnError) // final
                                                                                                       // RequestConfig
                                                                                                       // config, final
                                                                                                       // Record input)
            throws HTTPClientException {

        Optional<Token> oauthToken = Optional.empty();
        Optional<String> tokenCacheKey = queryConfiguration.getOAuthTokenCacheKey();
        if (tokenCacheKey.isPresent()) {
            oauthToken = Optional.ofNullable(tokenCache.get(tokenCacheKey.get()));
        }

        HTTPClient httpClient = HTTPClientFactory.create(queryConfiguration);
        if (oauthToken.isPresent() && !oauthToken.get().isExpired()) {
            // Set the token only if not expired
            httpClient.setOAuth20Token(oauthToken.get());
        }

        HTTPClient.HTTPResponse response = httpClient.invoke();

        // Is there a OAuth20 token to manage ?
        if (tokenCacheKey.isPresent() && response.getOAuth20Token().isPresent()) {
            // Don't understand why I have to cast to Token since Optional<Token>
            Token responseToken = (Token) response.getOAuth20Token().get();
            tokenCache.put(tokenCacheKey.get(), responseToken);
        }

        if (isDieOnError && (response.getStatus().getCode() / 100) != 2) {
            HTTPComponentException httpComponentException = new HTTPComponentException(
                    messages.responseStatusIsNotOK(response.getStatus().getCodeWithReason()));
            httpComponentException.setResponse(response);
            throw httpComponentException;
        }
        return response;
    }

    public QueryConfiguration convertConfiguration(final RequestConfig config, final Record input) {
        String url = buildUrl(config.getDataset().getDatastore().getBase(), config.getDataset().getResource());
        QueryConfigurationBuilder queryConfigurationBuilder = QueryConfigurationBuilder.create(url);
        queryConfigurationBuilder.setMethod(config.getDataset().getMethodType());

        // Timeouts
        queryConfigurationBuilder.setConnectionTimeout(config.getDataset().getDatastore().getConnectionTimeout());
        queryConfigurationBuilder.setReceiveTimeout(config.getDataset().getDatastore().getReceiveTimeout());

        // Authentication
        switch (config.getDataset().getDatastore().getAuthentication().getType()) {
        case Basic:
            queryConfigurationBuilder.setBasicAuthentication(
                    config.getDataset().getDatastore().getAuthentication().getBasic().getUsername(),
                    config.getDataset().getDatastore().getAuthentication().getBasic().getPassword());
            break;
        case Digest:
            queryConfigurationBuilder.setDigestAuthentication(
                    config.getDataset().getDatastore().getAuthentication().getBasic().getUsername(),
                    config.getDataset().getDatastore().getAuthentication().getBasic().getPassword());
            break;
        case NTLM:
            queryConfigurationBuilder.setNTLMAuthentication(
                    config.getDataset().getDatastore().getAuthentication().getNtlm().getUsername(),
                    config.getDataset().getDatastore().getAuthentication().getNtlm().getPassword());
            break;
        case Bearer:
            queryConfigurationBuilder.setAuthorizationToken("Bearer",
                    config.getDataset().getDatastore().getAuthentication().getBearerToken());
            break;
        case APIKey:
            APIKey apiKeyConf = config.getDataset().getDatastore().getAuthentication().getApiKey();
            Authorization.Destination destination = apiKeyConf.getDestination();
            String name = destination == Authorization.Destination.HEADERS ? apiKeyConf.getHeaderName()
                    : apiKeyConf.getQueryName();
            queryConfigurationBuilder.setAPIKey(
                    destination == Authorization.Destination.QUERY_PARAMETERS ? APIKeyDestination.QUERY_PARAMETERS
                            : APIKeyDestination.HEADERS,
                    name,
                    destination == Authorization.Destination.QUERY_PARAMETERS ? "" : apiKeyConf.getPrefix(),
                    apiKeyConf.getToken());
            break;
        case OAuth20:
            manageOAuth20(queryConfigurationBuilder, config);
            break;
        default:
            queryConfigurationBuilder.setNoAuthentication();
        }

        // Proxy
        if (config.getDataset().getDatastore().isUseProxy()) {
            configureProxyForQueryConfigurationBuilder(config, queryConfigurationBuilder);
        }

        // Certificate
        queryConfigurationBuilder
                .bypassCertificateConfiguration(config.getDataset().getDatastore().isBypassCertificateValidation());

        // Redirections
        boolean acceptRedirections = config.getDataset().isAcceptRedirections();
        queryConfigurationBuilder.acceptRedirection(acceptRedirections);
        if (acceptRedirections) {
            queryConfigurationBuilder
                    .setMaxNumberOfAcceptedRedirectionsOnSameURI(config.getDataset().getMaxRedirectOnSameURL())
                    .acceptOnlySameHostRedirection(config.getDataset().isOnlySameHost());
        }

        // Path parameters
        if (config.getDataset().isHasPathParams()) {
            config.getDataset()
                    .getPathParams()
                    .stream()
                    .forEach(p -> queryConfigurationBuilder.addPathParam(p.getKey(), p.getValue()));
        }

        // Query parameters
        if (config.getDataset().isHasQueryParams()) {
            config.getDataset()
                    .getQueryParams()
                    .stream()
                    .forEach(p -> queryConfigurationBuilder.addQueryParam(p.getKey(), p.getValue()));
        }

        // Headers
        if (config.getDataset().isHasHeaders()) {
            config.getDataset()
                    .getHeaders()
                    .stream()
                    .filter(h -> h.getKey() != null && !h.getKey().trim().isEmpty())
                    .filter(h -> h.getQuery() == Header.HEADER_QUERY_DESTINATION.MAIN
                            || h.getQuery() == Header.HEADER_QUERY_DESTINATION.BOTH)
                    .forEach(p -> queryConfigurationBuilder.addHeader(p.getKey(), p.getValue()));
        }

        // Body
        if (config.getDataset().isHasBody()) {
            RequestBody body = config.getDataset().getBody();
            switch (body.getType()) {
            case TEXT:
                queryConfigurationBuilder.setRawTextBody(body.getTextContent());
                break;
            case XML:
                queryConfigurationBuilder.setXMLBody(body.getTextContent());
                break;
            case JSON:
                queryConfigurationBuilder.setJSONBody(body.getTextContent());
                break;
            case FORM_DATA:
                body.getParams()
                        .stream()
                        .filter(p -> p.getKey() != null && !p.getKey().trim().isEmpty())
                        .forEach(
                                p -> queryConfigurationBuilder.addMultipartFormDataBodyParam(p.getKey(), p.getValue()));
                break;
            case X_WWW_FORM_URLENCODED:
                body.getParams()
                        .stream()
                        .forEach(p -> queryConfigurationBuilder.addXWWWFormURLEncodedBodyParam(p.getKey(),
                                p.getValue()));
                break;
            default:
                queryConfigurationBuilder.setRawTextBody(body.getTextContent());
            }
        }

        if (config.isUploadFiles()) {
            for (UploadFile uploadFile : config.getUploadFileTable()) {
                File file = new File(uploadFile.getFilePath());
                if (!file.exists() || file.isDirectory()) {
                    log.error(messages.noUploadFileExists(uploadFile.getFilePath()));
                    continue;
                }
                MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
                headers.add("Content-ID", uploadFile.getName());

                List<String> contentDispositionList = new ArrayList<>();
                contentDispositionList.add("attachment");
                contentDispositionList.add("file=\"" + uploadFile.getName() + "\"");
                contentDispositionList.add("filename=\"" + file.getName() + "\"");
                try {
                    contentDispositionList.add(
                            "filename*=UTF-8''\"" + URLEncoder.encode(file.getName(), "UTF-8") + "\"");
                } catch (UnsupportedEncodingException e) {
                    log.debug("Got an UnsupportedEncodingException for UTF-8, shouldn't be here", e);
                }
                headers.put("Content-Disposition", contentDispositionList);

                List<String> contentTypeList = new ArrayList<>();
                contentTypeList.add(uploadFile.getContentType());
                contentTypeList.add("charset=" + uploadFile.getEncoding());
                headers.put("Content-Type", contentTypeList);
                Attachment attachment = new Attachment(uploadFile.getName(),
                        new FileDataSource(file), headers);
                queryConfigurationBuilder.addAttachment(attachment);
            }
        }

        // Pagination
        if (config.getDataset().isHasPagination()) {
            Pagination pagination = config.getDataset().getPagination();
            Pagination.Strategy strategy = pagination.getStrategy();
            switch (strategy) {
            case OFFSET_LIMIT:
                OffsetLimitStrategyConfig offsetLimitStrategyConfig = pagination.getOffsetLimitStrategyConfig();
                queryConfigurationBuilder.setOffsetLimitPagination(
                        offsetLimitStrategyConfig.getLocation() == OffsetLimitStrategyConfig.Location.HEADERS
                                ? PaginationParametersLocation.HEADERS
                                : PaginationParametersLocation.QUERY_PARAMETERS,
                        offsetLimitStrategyConfig.getOffsetParamName(), offsetLimitStrategyConfig.getOffsetValue(),
                        offsetLimitStrategyConfig.getLimitParamName(), offsetLimitStrategyConfig.getLimitValue(),
                        offsetLimitStrategyConfig.getElementsPath());
                break;
            }
        }

        QueryConfiguration queryConfiguration;
        if (input == null) {
            queryConfiguration = queryConfigurationBuilder.build();
        } else {
            UnaryOperator<String> dictionary = ClassLoaderInvokeUtils.invokeInLoader(
                    () -> DictionaryProvider.getProvider()
                            .createDictionary(
                                    input, jsonParserFactory, recordBuilderFactory, true),
                    this.getClass().getClassLoader());
            Substitutor.PlaceholderConfiguration placeholderConfiguration =
                    new Substitutor.PlaceholderConfiguration(RecordBuilderService.CONTEXT_SUBSTITUTOR_INPUT_OPENER,
                            RecordBuilderService.CONTEXT_SUBSTITUTOR_INPUT_CLOSER,
                            RecordBuilderService.CONTEXT_SUBSTITUTOR_INPUT_PREFIX_KEY);
            Substitutor substitutor = new Substitutor(placeholderConfiguration, dictionary);
            queryConfiguration = queryConfigurationBuilder.build(substitutor);
        }

        // Support Content-encoding: gzip
        queryConfigurationBuilder.decompressResponsePayload(true);

        return queryConfiguration;
    }

    private static void configureProxyForQueryConfigurationBuilder(RequestConfig config,
            QueryConfigurationBuilder queryConfigurationBuilder) {
        String proxyHost = config.getDataset().getDatastore().getProxyConfiguration().getProxyHost();
        int proxyPort = config.getDataset().getDatastore().getProxyConfiguration().getProxyPort();
        if (config.getDataset()
                .getDatastore()
                .getProxyConfiguration()
                .getProxyType() == ProxyConfiguration.ProxyType.HTTP) {
            queryConfigurationBuilder.setHTTPProxy(proxyHost, proxyPort,
                    config.getDataset().getDatastore().getProxyConfiguration().getProxyLogin(),
                    config.getDataset().getDatastore().getProxyConfiguration().getProxyPassword());
        } else {
            queryConfigurationBuilder.setSOCKSProxy(proxyHost, proxyPort);
        }
    }

    private void manageOAuth20(QueryConfigurationBuilder builder, RequestConfig config) {
        OAuth20 oauth20 = config.getDataset().getDatastore().getAuthentication().getOauth20();

        List<KeyValuePair> headers = new ArrayList<>();
        if (config.getDataset().isHasHeaders()) {
            config.getDataset()
                    .getHeaders()
                    .stream()
                    .filter(h -> h.getKey() != null && !h.getKey().trim().isEmpty())
                    .filter(h -> h.getQuery() == Header.HEADER_QUERY_DESTINATION.AUTHENT
                            || h.getQuery() == Header.HEADER_QUERY_DESTINATION.BOTH)
                    .map(h -> new KeyValuePair(h.getKey(), h.getValue()))
                    .forEach(h -> headers.add(h));
        }

        switch (oauth20.getFlow()) {
        case CLIENT_CREDENTIAL:
            List<KeyValuePair> params = oauth20.getParams()
                    .stream()
                    .filter(p -> p.getKey() != null && !p.getKey().trim().isEmpty())
                    .map(p -> new KeyValuePair(p.getKey(), p.getValue()))
                    .collect(Collectors.toList());

            builder.setOAuth20ClientCredential(
                    org.talend.components.common.httpclient.api.authentication.OAuth20.AuthentMode
                            .valueOf(oauth20.getAuthenticationType().name()),
                    oauth20.getTokenEndpoint(),
                    oauth20.getClientId(),
                    oauth20.getClientSecret(),
                    params,
                    headers);
            break;
        }
    }

    public static String buildUrl(String base, String resource) {
        base = base.trim();

        if (resource == null || resource.isEmpty()) {
            return base;
        }

        resource = resource.trim();

        if (base.charAt(base.length() - 1) == '/' ^ resource.charAt(0) == '/') {
            return String.format("%s%s", base, resource);
        } else if (base.charAt(base.length() - 1) == '/' && resource.charAt(0) == '/') {
            return String.format("%s%s", base, resource.substring(1));
        } else {
            return String.format("%s/%s", base, resource);
        }
    }

}
