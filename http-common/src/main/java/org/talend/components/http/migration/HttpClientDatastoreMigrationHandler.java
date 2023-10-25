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

import java.util.Map;

import org.talend.sdk.component.api.component.MigrationHandler;

public class HttpClientDatastoreMigrationHandler implements MigrationHandler {

    @Override
    public Map<String, String> migrate(int incomingVersion, Map<String, String> incomingData) {
        if (incomingVersion < 2) {
            migrateProxyConfig(incomingData, "");
        }
        return incomingData;
    }

    static void migrateProxyConfig(Map<String, String> incomingData, String version1ProxyConfigPathPrefix) {
        incomingData.put(version1ProxyConfigPathPrefix + "proxyConfiguration.proxyType",
                incomingData.remove(version1ProxyConfigPathPrefix + "proxyType"));
        incomingData.put(version1ProxyConfigPathPrefix + "proxyConfiguration.proxyHost",
                incomingData.remove(version1ProxyConfigPathPrefix + "proxyHost"));
        incomingData.put(version1ProxyConfigPathPrefix + "proxyConfiguration.proxyPort",
                incomingData.remove(version1ProxyConfigPathPrefix + "proxyPort"));
        incomingData.put(version1ProxyConfigPathPrefix + "proxyConfiguration.proxyLogin",
                incomingData.remove(version1ProxyConfigPathPrefix + "proxyLogin"));
        incomingData.put(version1ProxyConfigPathPrefix + "proxyConfiguration.proxyPassword",
                incomingData.remove(version1ProxyConfigPathPrefix + "proxyPassword"));
    }
}
