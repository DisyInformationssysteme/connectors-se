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
package org.talend.components.http.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.talend.components.http.TestUtil;
import org.talend.components.http.configuration.Param;
import org.talend.components.http.server.ResourcesUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractHandler implements HttpHandler {

    public final static String REQUEST_FORM_KEY = OAuth2TokenHandler.class.getName() + ".request.form";

    protected Map<String, JsonObject> sharedData;

    public AbstractHandler(Map<String, JsonObject> sharedData) {
        this.sharedData = sharedData;
    }

    protected JsonObject exchange2Json(final HttpExchange exchange, boolean bodyIsHTTPForm) {
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();

        // I force headers name lowercase since I sent Content-Type in the cxf WebClient
        // And the query headers here was Content-type without any explanation.
        Map<String, String> headersFlat = exchange.getRequestHeaders()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> {
                    return e.getValue().stream().collect(Collectors.joining(", "));
                }));
        JsonObjectBuilder jsonHeaders = Json.createObjectBuilder();
        headersFlat.entrySet().stream().forEach(h -> {
            jsonHeaders.add(h.getKey(), h.getValue());
        });
        jsonBuilder.add("request-headers", jsonHeaders.build());

        String requestBody = ResourcesUtils.getString(exchange.getRequestBody());
        if (bodyIsHTTPForm) {
            Collection<Param> httpForm = TestUtil.queryToCollection(requestBody);
            JsonObjectBuilder httpFormJson = Json.createObjectBuilder();
            httpForm.stream().forEach(e -> {
                httpFormJson.add(e.getKey(), e.getValue());
            });
            jsonBuilder.add("request-body-object", httpFormJson.build());
        }
        jsonBuilder.add("request-body", requestBody);

        if (this.sharedData.containsKey(OAuth2TokenHandler.REQUEST_FORM_KEY)) {
            jsonBuilder.add("auhent-query", this.sharedData.get(OAuth2TokenHandler.REQUEST_FORM_KEY));
        }

        return jsonBuilder.build();
    }

}
