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

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import org.talend.components.common.httpclient.api.authentication.Token;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * This is the abstraction of the underlying HTTP client.
 * It can be usefull to retrieve the client #getNestedClient() to do do some more configuration not
 * supported by QueryConfiguration.
 * 
 * @param <C> The class of the real underlying client.
 */
public interface HTTPClient<C> {

    C getNestedClient();

    void setOAuth20Token(Token token);

    HTTPResponse invoke() throws HTTPClientException;

    interface HTTPResponse<R> {

        Status getStatus();

        boolean isSuccess();

        Map<String, String> getHeaders();

        String getBodyAsString() throws HTTPClientException;

        InputStream getBodyAsStream() throws HTTPClientException;

        R getNestedResponse();

        String getEncoding();

        void setOAuth20Token(Token token);

        Optional<Token> getOAuth20Token();

        /**
         * If a pagination strategy has been set, the HTTPResponse will contain the configuration to use to get the next
         * page.
         * The next page QueryConfiguration must be computed by the PaginationStrategy defined in the HTTPClient.
         * 
         * @return The HTTP query configuration to retrieve the next page.
         */
        Optional<QueryConfiguration> nextPageQueryConfiguration() throws HTTPClientException;

        /**
         * Return last number of retrieved element with the pagination strategie.
         */
        int getLastPageCount() throws HTTPClientException;

        /**
         * Get Content-Length value.
         *
         * @return Content-Length header value as integer if present and valid number. In other
         * cases returns {@code -1}.
         */
        default int getContentLength() {
            Object header = getHeaders().get("Content-Length");
            if (header != null) {
                try {
                    return Integer.parseInt(String.valueOf(header));
                } catch (NumberFormatException ignored) {
                    // return -1 when can't parse
                }
            }

            return -1;
        }
    }

    @AllArgsConstructor
    @Getter
    class Status {

        private final int code;

        private final String reason;

        private final String family;

        public String getCodeWithReason() {
            return String.format("%s %s", code, reason);
        }

    }

}
