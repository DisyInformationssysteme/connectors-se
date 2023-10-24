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
package org.talend.components.jdbc.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;

/**
 * the bean class for the database information
 */
@Data
@NoArgsConstructor
@Documentation("JDBC component configuration")
public class JDBCConfiguration implements Serializable {

    @Option
    @Documentation("supported table types. used in table name suggestions")
    private Set<String> supportedTableTypes;

    @Option
    @Documentation("list of driver meta data")
    private List<Driver> drivers = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(of = { "id", "className" })
    @Documentation("Jdbc driver metadata")
    public static class Driver implements Serializable {

        @Option
        @Documentation("Jdbc driver id. this is must be unique")
        private String id;

        @Option
        @Documentation("Jdbc driver display Name.")
        private String displayName;

        @Option
        @Documentation("Jdbc driver order in the list")
        private Integer order = Integer.MAX_VALUE;

        @Option
        @Documentation("Jdbc driver class")
        private String className;

        @Option
        @Documentation("Jdbc driver that can handle this db also")
        private List<String> handlers = new ArrayList<>();

        @Option
        @Documentation("Jdbc driver and driver dependencies jar locations in mvn format")
        private List<String> paths = new ArrayList<>();

        @Option
        @Documentation("Fixed jdbc url parameters")
        private List<KeyVal> fixedParameters = new ArrayList<>();

        @Option
        @Documentation("JDBC default protocol")
        private String protocol;

        @Option
        @Documentation("Defaults values")
        private Defaults defaults;

        public String getDisplayName() {
            return ofNullable(displayName).filter(d -> !d.isEmpty()).orElse(id);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Documentation("Key/Value class")
    public static class KeyVal implements Serializable {

        @Option
        @Documentation("The key")
        private String key;

        @Option
        @Documentation("The value")
        private String value;
    }

    @Data
    @NoArgsConstructor
    @Documentation("Default Values for connection")
    public static class Defaults implements Serializable {

        @Option
        @Documentation("JDBC default host")
        private String host;

        @Option
        @Documentation("JDBC default port")
        private int port;

        @Option
        @Documentation("JDBC default database")
        private String database;

        @Option
        @Documentation("JDBC default parameters")
        private List<KeyVal> parameters = new ArrayList<>();
    }

}
