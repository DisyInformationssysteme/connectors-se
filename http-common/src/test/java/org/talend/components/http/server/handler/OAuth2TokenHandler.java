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
import org.talend.components.common.httpclient.api.authentication.OAuth20;
import org.talend.components.http.TestUtil;
import org.talend.components.http.configuration.Param;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class OAuth2TokenHandler extends AbstractHandler {

    public final static String SCOPE_READ_WRITE = "read write";

    public final static String RESOURCE = "my_resource";

    public final static String CLIENT_ID = "a_client_id";

    public final static String CLIENT_SECRET = "a_client_secret";

    public OAuth2TokenHandler(Map<String, JsonObject> sharedData) {
        super(sharedData);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        JsonObject json = exchange2Json(exchange, true);
        Collection<Param> formParam = TestUtil.queryToCollection(json.getString("request-body"));
        this.sharedData.put(REQUEST_FORM_KEY, json);

        int status = HttpURLConnection.HTTP_UNAUTHORIZED;
        String payload = "";

        Optional<Param> clientId = formParam.stream()
                .filter(p -> OAuth20.Keys.client_id.name().equals(p.getKey()))
                .filter(p -> CLIENT_ID.equals(p.getValue()))
                .findFirst();
        Optional<Param> clientSecret = formParam.stream()
                .filter(p -> OAuth20.Keys.client_secret.name().equals(p.getKey()))
                .filter(p -> CLIENT_SECRET.equals(p.getValue()))
                .findFirst();
        Optional<Param> scopes = formParam.stream()
                .filter(p -> OAuth20.Keys.scope.name().equals(p.getKey()))
                .filter(p -> SCOPE_READ_WRITE.equals(p.getValue()))
                .findFirst();
        Optional<Param> resource = formParam.stream()
                .filter(p -> OAuth20.Keys.resource.name().equals(p.getKey()))
                .filter(p -> RESOURCE.equals(p.getValue()))
                .findFirst();

        if (clientId.isPresent() && clientSecret.isPresent() && scopes.isPresent() && resource.isPresent()) {
            status = HttpURLConnection.HTTP_OK;
            payload = TestUtil.loadResource("/org/talend/components/http/server/oauth_token_success.json");
        } else {
            payload = TestUtil.loadResource("/org/talend/components/http/server/oauth_token_err.json");
        }

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();

    }
}
