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
package org.talend.components.http.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.talend.sdk.component.api.component.MigrationHandler;

public class HttpClientDatastoreMigrationHandler implements MigrationHandler {

    @Override
    public Map<String, String> migrate(int incomingVersion, Map<String, String> incomingData) {
        if (incomingVersion < 2) {
            migrateProxyConfig(incomingData, "");
        }

        if (incomingVersion < 3) {
            migrateOAuthScopesToAdditionalParams(incomingData, "");
        }
        return incomingData;
    }

    static void migrateProxyConfig(Map<String, String> incomingData, String version1ProxyConfigPathPrefix) {
        putIfNotNull(incomingData, version1ProxyConfigPathPrefix + "proxyType",
                version1ProxyConfigPathPrefix + "proxyConfiguration.proxyType");
        putIfNotNull(incomingData, version1ProxyConfigPathPrefix + "proxyHost",
                version1ProxyConfigPathPrefix + "proxyConfiguration.proxyHost");
        putIfNotNull(incomingData, version1ProxyConfigPathPrefix + "proxyPort",
                version1ProxyConfigPathPrefix + "proxyConfiguration.proxyPort");
        putIfNotNull(incomingData, version1ProxyConfigPathPrefix + "proxyLogin",
                version1ProxyConfigPathPrefix + "proxyConfiguration.proxyLogin");
        putIfNotNull(incomingData, version1ProxyConfigPathPrefix + "proxyPassword",
                version1ProxyConfigPathPrefix + "proxyConfiguration.proxyPassword");
    }

    static void migrateOAuthScopesToAdditionalParams(Map<String, String> incomingData,
            String version1ProxyConfigPathPrefix) {

        // Scopes are added in oauth20.params
        String scopes = incomingData.entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(version1ProxyConfigPathPrefix + "authentication.oauth20.scopes"))
                .map(e -> e.getValue())
                .collect(Collectors.joining(" "));

        if (!scopes.trim().isEmpty()) {
            incomingData.put(version1ProxyConfigPathPrefix + "authentication.oauth20.params[0].key", "scope");
            incomingData.put(version1ProxyConfigPathPrefix + "authentication.oauth20.params[1].value", scopes);
        }

    }

    private static void putIfNotNull(Map<String, String> configMap, String from, String to) {
        if (configMap.containsKey(from) && configMap.get(from) != null) {
            configMap.put(to, configMap.remove(from));
        }
    }

}
