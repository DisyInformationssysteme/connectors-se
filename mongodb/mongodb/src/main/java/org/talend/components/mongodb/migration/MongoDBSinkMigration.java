package org.talend.components.mongodb.migration;

import org.talend.components.mongo.WriteConcern;
import org.talend.sdk.component.api.component.MigrationHandler;

import java.util.Map;

public class MongoDBSinkMigration implements MigrationHandler {
    @Override
    public Map<String, String> migrate(int incomingVersion, Map<String, String> incomingData) {
        if(incomingVersion == 1){
            String isSetWriteConcern = incomingData.get("setWriteConcern");
            String writeConcern = incomingData.get("writeConcern");
            if("true".equals(isSetWriteConcern) && "REPLICA_ACKNOWLEDGED".equals(writeConcern)){
                incomingData.put("writeConcern", String.valueOf(WriteConcern.W2));
            }
        }
        return incomingData;
    }
}
