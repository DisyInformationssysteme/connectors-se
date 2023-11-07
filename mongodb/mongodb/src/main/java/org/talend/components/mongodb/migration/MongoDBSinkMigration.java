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
package org.talend.components.mongodb.migration;

import org.talend.components.mongo.WriteConcern;
import org.talend.sdk.component.api.component.MigrationHandler;

import java.util.Map;

public class MongoDBSinkMigration implements MigrationHandler {

    @Override
    public Map<String, String> migrate(int incomingVersion, Map<String, String> incomingData) {
        if (incomingVersion < 2) {
            String isSetWriteConcern = incomingData.get("configuration.setWriteConcern");
            String writeConcern = incomingData.get("configuration.writeConcern");
            if ("true".equals(isSetWriteConcern) && "REPLICA_ACKNOWLEDGED".equals(writeConcern)) {
                incomingData.put("configuration.writeConcern", String.valueOf(WriteConcern.W2));
            }
        }
        return incomingData;
    }
}
