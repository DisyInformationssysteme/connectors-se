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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ResourcesUtils {

    public static String loadResource(String name) {
        InputStream resourceAsStream = ResourcesUtils.class.getResourceAsStream(name);
        String content = getString(resourceAsStream);
        return content;
    }

    public static String getString(InputStream resourceAsStream) {
        InputStreamReader isr = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        String content = br.lines().collect(Collectors.joining("\n"));
        return content;
    }

    public static JsonObject getJsonObject(InputStream resourceAsStream) {
        String str = getString(resourceAsStream);
        return getJsonObject(str);
    }

    public static JsonObject getJsonObject(String str) {
        StringReader sr = new StringReader(str);
        return getJsonObject(sr);
    }

    public static JsonObject getJsonObject(StringReader stringReader) {
        JsonReader reader = Json.createReader(stringReader);
        JsonObject jsonObject = reader.readObject();
        return jsonObject;
    }

}
