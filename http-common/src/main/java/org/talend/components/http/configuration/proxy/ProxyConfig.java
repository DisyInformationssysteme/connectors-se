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
package org.talend.components.http.configuration.proxy;

import java.io.Serializable;

import org.talend.components.common.httpclient.api.ProxyConfiguration;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import lombok.Data;

@Data
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row({ "proxyType" }),
        @GridLayout.Row({ "proxyHost", "proxyPort" }),
        @GridLayout.Row({ "proxyLogin", "proxyPassword" })
})
public class ProxyConfig implements Serializable {

    @Option
    @Documentation("Proxy's type.")
    @DefaultValue("HTTP")
    private ProxyConfiguration.ProxyType proxyType = ProxyConfiguration.ProxyType.HTTP;

    // TODO: remove default value once https://jira.talendforge.org/browse/TCOMP-2260 is done.
    @Option
    @Documentation("Proxy's host.")
    private String proxyHost;

    @Option
    @Documentation("Proxy's port.")
    @DefaultValue("443")
    private int proxyPort = 443;

    @Option
    @Documentation("Proxy's login.")
    // TODO: Currently, SOCKS with required authentication is not working:
    // https://jira.talendforge.org/browse/TDI-48466
    @ActiveIf(target = "proxyType", value = "HTTP")
    private String proxyLogin;

    @Option
    @Documentation("Proxy's password.")
    @Credential
    @ActiveIf(target = "proxyType", value = "HTTP")
    private String proxyPassword;

}
