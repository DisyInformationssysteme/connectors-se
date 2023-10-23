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
package org.talend.components.jdbc.platforms;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.JDBCConfiguration;
import org.talend.components.jdbc.schema.Dbms;
import org.talend.components.jdbc.service.I18nMessage;

import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class GenericPlatform extends Platform {

    public static final String GENERIC = "generic";

    public GenericPlatform(final I18nMessage i18n, final JDBCConfiguration.Driver driver) {
        super(i18n, driver);
    }

    @Override
    public String name() {
        return GENERIC;
    }

    @Override
    public String delimiterToken() {
        return "";
    }

    @Override
    public String buildQuery(final Connection connection, final Table table, final boolean useOriginColumnName,
            Dbms mapping)
            throws SQLException {
        throw new UnsupportedOperationException("not support to generate to create table sql for generic jdbc case");
    }

    @Override
    public boolean isTableExistsCreationError(final Throwable e) {
        return false;
    }

}
