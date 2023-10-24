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
package org.talend.components.jdbc.datastore;

import lombok.Data;
import lombok.ToString;
import org.talend.components.jdbc.common.AuthenticationType;
import org.talend.components.jdbc.common.Driver;
import org.talend.components.jdbc.common.GrantType;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Checkable;
import org.talend.sdk.component.api.configuration.action.Proposable;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.condition.ActiveIfs;
import org.talend.sdk.component.api.configuration.condition.UIScope;
import org.talend.sdk.component.api.configuration.constraint.Min;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.talend.sdk.component.api.configuration.condition.ActiveIfs.Operator.AND;
import static org.talend.sdk.component.api.configuration.condition.ActiveIfs.Operator.OR;

@Data
@ToString(exclude = { "password", "privateKey", "privateKeyPassword" })
@GridLayout({
        @GridLayout.Row("enableDBType"),

        // cloud special
        @GridLayout.Row({ "dbType", "handler" }),
        @GridLayout.Row("setRawUrl"),

        @GridLayout.Row("jdbcUrl"),

        // cloud special
        @GridLayout.Row("host"),
        @GridLayout.Row("port"),
        @GridLayout.Row("database"),
        @GridLayout.Row("parameters"),

        @GridLayout.Row("jdbcDriver"),
        @GridLayout.Row("jdbcClass"),
        @GridLayout.Row("userId"),
        @GridLayout.Row("password"),
        @GridLayout.Row("useSharedDBConnection"),
        @GridLayout.Row("sharedDBConnectionName"),
        @GridLayout.Row("useDataSource"),
        @GridLayout.Row("dataSourceAlias"),
        @GridLayout.Row("dbMapping"),

        // cloud special
        @GridLayout.Row("authenticationType"),
        @GridLayout.Row("privateKey"),
        @GridLayout.Row("privateKeyPassword"),
        @GridLayout.Row("oauthTokenEndpoint"),
        @GridLayout.Row("clientId"),
        @GridLayout.Row("clientSecret"),
        @GridLayout.Row("grantType"),
        @GridLayout.Row("oauthUsername"),
        @GridLayout.Row("oauthPassword"),
        @GridLayout.Row("scope")
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row("useAutoCommit"),
        @GridLayout.Row("autoCommit"),

        // cloud special
        @GridLayout.Row({ "defineProtocol", "protocol" }),
        @GridLayout.Row("connectionTimeOut"),
        @GridLayout.Row("connectionValidationTimeOut")
})
@DataStore("JDBCDataStore")
@Checkable("CheckConnection")
@Documentation("A connection to a database")
public class JDBCDataStore implements Serializable {

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.CLOUD_SCOPE })
    @Documentation("Enable DB Type.")
    private boolean enableDBType;

    // Cloud jdbc special options start: ======================

    @Option
    @ActiveIf(target = "enableDBType", value = { "true" })
    @Documentation("Data base type from the supported data base list.")
    @Proposable("ACTION_LIST_SUPPORTED_DB")
    private String dbType;

    @Option
    @ActiveIf(target = "dbType", value = { "Aurora", "SingleStore" })
    @Documentation("Database handlers, this configuration is for cloud databases that support the use of other databases drivers.")
    @Suggestable(value = "ACTION_LIST_HANDLERS_DB", parameters = { "dbType" })
    private String handler;

    @Option
    @ActiveIf(target = "enableDBType", value = { "true" })
    @Documentation("Let user define complete jdbc url or not")
    @DefaultValue("true")
    private Boolean setRawUrl = true;

    @Option
    @ActiveIf(target = "setRawUrl", value = { "false" })
    @Documentation("jdbc host")
    private String host;

    @Option
    @ActiveIf(target = "setRawUrl", value = { "false" })
    @Documentation("jdbc port")
    @DefaultValue("80")
    private int port = 80;

    @Option
    @ActiveIf(target = "setRawUrl", value = { "false" })
    @Documentation("jdbc database")
    private String database;

    @Option
    @ActiveIf(target = "setRawUrl", value = { "false" })
    @Documentation("jdbc parameters")
    private List<JDBCConfiguration.KeyVal> parameters = new ArrayList<>();

    @Option
    @Documentation("Let user define protocol of the jdbc url.")
    @DefaultValue("false")
    @ActiveIf(target = "setRawUrl", value = { "false" })
    private Boolean defineProtocol = false;

    @Option
    @ActiveIfs(value = { @ActiveIf(target = "setRawUrl", value = { "false" }),
            @ActiveIf(target = "defineProtocol", value = { "true" }) })
    @Documentation("Protocol")
    private String protocol;

    @Option
    @DefaultValue("BASIC")
    @ActiveIf(target = "dbType", value = "Snowflake")
    @Documentation("Authentication type.")
    private AuthenticationType authenticationType;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "KEY_PAIR") })
    @Credential
    @Documentation("Private key.")
    private String privateKey;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "KEY_PAIR") })
    @Credential
    @Documentation("Private key password.")
    private String privateKeyPassword;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH") })
    @Documentation("Oauth token endpoint.")
    private String oauthTokenEndpoint;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH") })
    @Documentation("Client ID.")
    private String clientId;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH") })
    @Credential
    @Documentation("Client secret.")
    private String clientSecret;

    @Option
    @DefaultValue("CLIENT_CREDENTIALS")
    @ActiveIfs(value = { @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH") }, operator = AND)
    @Documentation("Grant type.")
    private GrantType grantType;

    @Option
    @ActiveIfs(value = { @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH"),
            @ActiveIf(target = "grantType", value = "PASSWORD") }, operator = AND)
    @Documentation("OAuth username.")
    private String oauthUsername;

    @Option
    @ActiveIfs(value = { @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH"),
            @ActiveIf(target = "grantType", value = "PASSWORD") }, operator = AND)
    @Credential
    @Documentation("OAuth password.")
    private String oauthPassword;

    @Option
    @ActiveIfs({ @ActiveIf(target = "dbType", value = "Snowflake"),
            @ActiveIf(target = "authenticationType", value = "OAUTH") })
    @Documentation("Scope.")
    private String scope;

    @Min(0)
    @Option
    @ActiveIf(target = "enableDBType", value = { "true" })
    @Documentation("Set the maximum number of seconds that a client will wait for a connection from the pool. "
            + "If this time is exceeded without a connection becoming available, a SQLException will be thrown from DataSource.getConnection().")
    private long connectionTimeOut = 30;

    @Min(0)
    @Option
    @ActiveIf(target = "enableDBType", value = { "true" })
    @Documentation("Sets the maximum number of seconds that the pool will wait for a connection to be validated as alive.")
    private long connectionValidationTimeOut = 10;

    // Cloud jdbc special options end: ======================

    @Option
    @ActiveIf(target = "setRawUrl", value = { "true" })
    @Documentation("jdbc url")
    private String jdbcUrl = "jdbc:";

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Documentation("jdbc driver table")
    private List<Driver> jdbcDriver = Collections.emptyList();

    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_SCOPE })
    @Option
    @Suggestable(value = "GUESS_DRIVER_CLASS", parameters = { "jdbcDriver" })
    @Documentation("driver class")
    private String jdbcClass;

    @Option
    @ActiveIfs(value = { @ActiveIf(target = "dbType", value = "Snowflake", negate = true),
            @ActiveIf(target = "authenticationType", value = "OAUTH", negate = true) }, operator = OR)
    @Documentation("database user")
    private String userId;

    @Option
    @ActiveIfs(value = { @ActiveIf(target = "dbType", value = "Snowflake", negate = true),
            @ActiveIf(target = "authenticationType", value = "BASIC") }, operator = OR)
    @Credential
    @Documentation("database password")
    private String password;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_CONNECTION_COMPONENT_SCOPE })
    @Documentation("use or register a shared DB connection")
    private boolean useSharedDBConnection;

    @Option
    @ActiveIfs(operator = AND, value = { @ActiveIf(target = "useSharedDBConnection", value = { "true" }),
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_CONNECTION_COMPONENT_SCOPE }) })
    @Documentation("shared DB connection name for register or fetch")
    private String sharedDBConnectionName;

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_COMPONENT_SCOPE })
    @Documentation("use data source")
    private boolean useDataSource;

    @Option
    @ActiveIfs(operator = AND, value = { @ActiveIf(target = "useDataSource", value = { "true" }),
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_COMPONENT_SCOPE }) })
    @Documentation("data source alias for fetch")
    private String dataSourceAlias;

    // now and future, only jdbc connector need this widget type : studio "widget.type.mappingType", so not generic, so
    // hard code in studio is ok now,
    // no need to provide tck framework support
    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_METADATA_SCOPE })
    @Documentation("select db mapping file for type convert")
    private String dbMapping;

    // advanced setting

    @Option
    @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_CONNECTION_COMPONENT_SCOPE })
    @Documentation("decide if call auto commit method")
    private boolean useAutoCommit = true;

    @Option
    @ActiveIfs(operator = AND, value = { @ActiveIf(target = "useAutoCommit", value = { "true" }),
            @ActiveIf(target = UIScope.TARGET, value = { UIScope.STUDIO_CONNECTION_COMPONENT_SCOPE }) })
    @Documentation("if true, mean auto commit, else disable auto commit, as different database, default auto commit value is different")
    private boolean autoCommit;

    public String getJdbcUrl() {
        return jdbcUrl == null ? null : jdbcUrl.trim();
    }

}
