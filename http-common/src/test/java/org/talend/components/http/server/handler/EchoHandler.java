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

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class EchoHandler extends AbstractHandler {

    public EchoHandler(Map<String, JsonObject> sharedData) {
        super(sharedData);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JsonObject json = exchange2Json(exchange, false);

        StringWriter writer = new StringWriter();
        Json.createWriter(writer).write(json);
        byte[] payload = writer.toString().getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, payload.length);
        OutputStream os = exchange.getResponseBody();
        os.write(payload);
        os.close();
    }
}
