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
package org.talend.components.common.httpclient.api.authentication;

public class OAuth20 {

    public final static String BEARER = "Bearer";

    public enum AuthentMode {
        FORM,
        BASIC,
        DIGEST
    }

    public enum Keys {
        client_id,
        client_secret,
        grant_type,
        scope,
        resource,
        audience
    }

    public enum successToken {
        access_token,
        token_type,
        expires_in
    }

    public enum errorToken {
        error,
        error_description,
        error_uri
    }

    public enum GrantType {
        client_credentials;
    }
}
