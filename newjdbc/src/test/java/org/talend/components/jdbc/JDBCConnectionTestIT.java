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
package org.talend.components.jdbc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.jdbc.datastore.JDBCDataStore;
import org.talend.components.jdbc.service.JDBCService;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@WithComponents("org.talend.components.jdbc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Testing of JDBC connection component")
public class JDBCConnectionTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private JDBCService jdbcService;

    private JDBCDataStore dataStore;

    @BeforeAll
    public void beforeClass() throws Exception {
        dataStore = DBTestUtils.createDataStore(false);
    }

    @Test
    public void testConnection() throws SQLException {
        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {

        }
    }

    @Test
    public void testConnectionWithEmptyJDBCURL() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setJdbcUrl("");

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testConnectionWithEmptyDriver() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setJdbcClass(null);

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testConnectionWithWrongDriver() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setJdbcClass("wrongDriver");

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testConnectionWithWrongURL() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setJdbcUrl("wrongUrl");

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testNotAutoCommit() throws IOException, SQLException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setUseAutoCommit(true);
        dataStore.setAutoCommit(false);

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            assertTrue(!dataSourceWrapper.getConnection().getAutoCommit());
            assertTrue(!dataSourceWrapper.getConnection().isClosed());
        }
    }

    @Test
    public void testAutoCommit() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);

        dataStore.setUseAutoCommit(true);
        dataStore.setAutoCommit(true);

        try (JDBCService.DataSourceWrapper dataSourceWrapper = jdbcService.createDataSource(dataStore)) {
            assertTrue(dataSourceWrapper.getConnection().getAutoCommit());
            assertTrue(!dataSourceWrapper.getConnection().isClosed());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEliminateSpaceURL() throws IOException {
        JDBCDataStore dataStore = DBTestUtils.createDataStore(false);
        dataStore.setJdbcUrl(" a_value_with_space_around_it. ");
        assertTrue("a_value_with_space_around_it.".equals(dataStore.getJdbcUrl()));
    }

}
