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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpClientMigrationTest {

    @Test
    public void testMigrateDatasetProxy() {
        Map<String,String> version1ProxyConfig = new HashMap<>();

        String typeValue = "valueType";
        String hostValue = "valueHost";
        String portValue = "123";
        String loginValue = "valueLogin";
        String passwordValue = "valuePass";
        version1ProxyConfig.put("proxyType", typeValue);
        version1ProxyConfig.put("proxyHost", hostValue);
        version1ProxyConfig.put("proxyPort", portValue);
        version1ProxyConfig.put("proxyLogin", loginValue);
        version1ProxyConfig.put("proxyPassword", passwordValue);

        HttpClientDatastoreMigrationHandler migrator = new HttpClientDatastoreMigrationHandler();

        migrator.migrate(1, version1ProxyConfig);

        Assertions.assertEquals(typeValue, version1ProxyConfig.get("proxyConfiguration.proxyType"));
        Assertions.assertEquals(hostValue, version1ProxyConfig.get("proxyConfiguration.proxyHost"));
        Assertions.assertEquals(portValue, version1ProxyConfig.get("proxyConfiguration.proxyPort"));
        Assertions.assertEquals(loginValue, version1ProxyConfig.get("proxyConfiguration.proxyLogin"));
        Assertions.assertEquals(passwordValue, version1ProxyConfig.get("proxyConfiguration.proxyPassword"));
    }
}