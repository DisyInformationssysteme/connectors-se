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
package org.talend.components.http.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.http.server.handler.EchoHandler;
import org.talend.components.http.server.handler.OAuth2TokenHandler;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BasicHTTPServerFactory {

    public final static String HELLO_WORLD = "Hello world!";

    public final static String HTTP_ECHO = "/echo";

    public final static String HTTP_OAUTH_TOKEN = "/oauth/token";

    private static BasicHTTPServerFactory instance;

    private static Map<String, JsonObject> sharedData = new HashMap<>();

    private BasicHTTPServerFactory() {
        /** Don't instantiate **/
    }

    public static synchronized BasicHTTPServerFactory getInstance() {
        if (instance == null) {
            instance = new BasicHTTPServerFactory();
        }

        return instance;
    }

    public HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            configureServer(server);

            return server;
        } catch (IOException e) {
            log.error(String.format("Can't start the HttpServer from %s : %s",
                    BasicHTTPServerFactory.class.getName(), e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    private static void configureServer(HttpServer server) {
        echoContext(server);
        oauthTokenContext(server);
    }

    private static void echoContext(HttpServer server) {
        server.createContext(HTTP_ECHO, new EchoHandler(sharedData));
    }

    private static void oauthTokenContext(HttpServer server) {
        server.createContext(HTTP_OAUTH_TOKEN, new OAuth2TokenHandler(sharedData));
    }

}
