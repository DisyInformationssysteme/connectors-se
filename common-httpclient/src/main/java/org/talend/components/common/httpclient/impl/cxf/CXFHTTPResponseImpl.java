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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.talend.components.common.httpclient.api.ContentType;
import org.talend.components.common.httpclient.api.HTTPClient;
import org.talend.components.common.httpclient.api.HTTPClientException;
import org.talend.components.common.httpclient.api.QueryConfiguration;
import org.talend.components.common.httpclient.pagination.PaginationStrategy;
import org.talend.components.common.httpclient.api.authentication.Token;

class CXFHTTPResponseImpl implements HTTPClient.HTTPResponse<Response> {

    private Response response;

    private HTTPClient.Status status;

    private Map<String, String> headers;

    private String encoding;

    private Token token;

    private byte[] payload;

    private final PaginationStrategy paginationStrategy;

    public CXFHTTPResponseImpl(final Response response, PaginationStrategy paginationStrategy) {
        this.response = response;
        this.paginationStrategy = paginationStrategy;
        computeResponse();
    }

    private void computeResponse() {
        computeStatus();
        computeHeaders();
    }

    private void computeStatus() {
        this.status = new HTTPClient.Status(response.getStatus(), response.getStatusInfo().getReasonPhrase(),
                response.getStatusInfo().getFamily().name());
    }

    private void computeHeaders() {
        this.headers = response.getHeaders().entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> {
            // Convert multi values to String with ',' as separator
            return e.getValue().stream().map(Object::toString).collect(Collectors.joining(";"));
        }));

        encoding = ContentType.getCharsetName(this.headers);
    }

    @Override
    public HTTPClient.Status getStatus() {
        return this.status;
    }

    @Override
    public boolean isSuccess() {
        return response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getBodyAsString() throws HTTPClientException {
        loadPayload();
        try {
            return new String(this.payload, 0, this.payload.length, this.getEncoding());
        } catch (UnsupportedEncodingException e) {
            throw new HTTPClientException(
                    String.format("Can't convert HTTP response payload to string with that encoding '%s' : %s",
                            this.encoding, e.getMessage()),
                    e);
        }

    }

    @Override
    public InputStream getBodyAsStream() throws HTTPClientException {
        loadPayload();
        return new ByteArrayInputStream(this.payload);
    }

    private void loadPayload() throws HTTPClientException {
        try {
            if (this.payload == null) {
                InputStream inputStream = (InputStream) response.getEntity();
                if (inputStream == null) {
                    inputStream = new ByteArrayInputStream(new byte[] {});
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                baos.close();
                this.payload = baos.toByteArray();
            }
        } catch (IOException e) {
            throw new HTTPClientException("Can't load HTTP response payload: " + e.getMessage(), e);
        }
    }

    @Override
    public Response getNestedResponse() {
        return response;
    }

    @Override
    public String getEncoding() {
        return this.encoding;
    }

    @Override
    public void setOAuth20Token(Token token) {
        this.token = token;
    }

    @Override
    public Optional<Token> getOAuth20Token() {
        return Optional.ofNullable(this.token);
    }

    @Override
    public Optional<QueryConfiguration> nextPageQueryConfiguration() throws HTTPClientException {
        return this.paginationStrategy.getNextPageConfiguration(this);
    }

    @Override
    public int getLastPageCount() throws HTTPClientException {
        return this.paginationStrategy.getLastCount(this);
    }

}
