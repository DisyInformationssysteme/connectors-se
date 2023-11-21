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
package org.talend.components.http;

import com.sun.net.httpserver.HttpExchange;
import org.talend.components.http.configuration.Param;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestUtil {

    private TestUtil() {
    }

    /**
     *
     * @param query
     * @return Extract from the given string the HTTP form key/values and create a MAp from them.
     */
    public static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();

        Collection<Param> collection = queryToCollection(query);
        collection.stream().forEach(e -> result.put(e.getKey(), e.getValue()));

        return result;
    }

    public static Collection<Param> queryToCollection(String query) {
        if (query == null) {
            return Collections.emptyList();
        }
        List<Param> result = new ArrayList<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                String decoded = null;
                try {
                    decoded = java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    decoded = "Fail to decode";
                }
                result.add(new Param(entry[0], decoded));
            } else {
                result.add(new Param(entry[0], ""));
            }
        }
        return result;
    }

    /**
     *
     * @param exchange
     * @return The Map<String, String> representation of the HTTP form presentin the body.
     */
    public static Map<String, String> formToMap(final HttpExchange exchange) {
        String requestBody = bodyToString(exchange);
        return queryToMap(requestBody);
    }

    /**
     *
     * @param exchange
     * @return The body of the given HttpExchange as a String.
     */
    public static String bodyToString(final HttpExchange exchange) {
        return getString(exchange.getRequestBody());
    }

    /**
     *
     * @param name The resource file name.
     * @return The content of the resource file as String.
     */
    public static String loadResource(String name) {
        InputStream resourceAsStream = TestUtil.class.getResourceAsStream(name);
        String content = getString(resourceAsStream);
        return content;
    }

    /**
     *
     * @param resourceAsStream
     * @return The UTF-8 String generated from the given InputStream.
     */
    public static String getString(InputStream resourceAsStream) {
        InputStreamReader isr = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        String content = br.lines().collect(Collectors.joining("\n"));
        return content;
    }

    /**
     * @param exchange
     * @return A map of header where keys are lowercase, and all values of a header is concatenated with ','.
     */
    public static Map<String, String> getRequestHeaders(final HttpExchange exchange) {
        return exchange.getRequestHeaders()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(),
                        e -> e.getValue().stream().collect(Collectors.joining(","))));

    }
}
