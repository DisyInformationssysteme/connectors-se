/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
package org.talend.components.adlsgen2.datastore;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.adlsgen2.AdlsGen2TestBase;
import org.talend.components.adlsgen2.datastore.Constants.MethodConstants;
import org.talend.sdk.component.junit5.WithComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithComponents("org.talend.components.adlsgen2")
class SharedKeyCredentialsUtilsTest extends AdlsGen2TestBase {

    public static final String USERNAME = "username";

    private SharedKeyUtils utils;

    private URL url;

    private Map<String, String> headers;

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        utils = new SharedKeyUtils(USERNAME, accountKey);
        url = new URL(
                "https://undxgen2.dfs.core.windows.net/adls-gen2?directory=myNewFolder&recursive=false&resource=filesystem&timeout=60");
        headers = new HashMap<>();
    }

    @Test
    void getAccountName() {
        assertEquals(USERNAME, utils.getAccountName());
    }

    @Test
    void buildAuthenticationSignature() throws Exception {
        String signature = utils.buildAuthenticationSignature(url, MethodConstants.GET, headers);
        assertNotNull(signature);
        assertTrue(signature.startsWith("SharedKey username:"));
    }
}