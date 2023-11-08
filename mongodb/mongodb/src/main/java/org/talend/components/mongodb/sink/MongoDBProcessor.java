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
package org.talend.components.mongodb.sink;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.talend.components.common.stream.output.json.RecordToJson;
import org.talend.components.mongo.BulkWriteType;
import org.talend.components.mongo.Mode;
import org.talend.components.mongo.output.MongoCommonOutput;
import org.talend.components.mongo.service.RecordToDocument;
import org.talend.components.mongodb.dataset.MongoDBReadAndWriteDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.migration.MongoDBSinkMigration;
import org.talend.components.mongodb.service.I18nMessage;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;
import org.talend.sdk.component.api.record.Record;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.WriteConcern.*;

@Version(value = 2, migrationHandler = MongoDBSinkMigration.class)
@Slf4j
@Icon(value = Icon.IconType.CUSTOM, custom = "mongo_db-connector")
@Processor(name = "Sink")
@Documentation("This component writes data to MongoDB")
public class MongoDBProcessor extends MongoCommonOutput {

    private I18nMessage i18n;

    private final MongoDBSinkConfiguration configuration;

    private final MongoDBService service;

    private transient MongoClient client;

    private transient MongoCollection<Document> collection;

    public MongoDBProcessor(@Option("configuration") final MongoDBSinkConfiguration configuration,
            final MongoDBService service,
            final I18nMessage i18n) {
        super.configuration = this.configuration = configuration;
        this.service = service;
        this.i18n = i18n;
    }

    @PostConstruct
    public void init() {
        recordToJson = new RecordToJson();
        recordToDocument = new RecordToDocument();

        MongoDBReadAndWriteDataSet dataset = configuration.getDataset();
        MongoDBDataStore datastore = dataset.getDatastore();
        client = service.createClient(datastore);
        MongoDatabase database = client.getDatabase(datastore.getDatabase());

        // apply to database level too, necessay?
        if (configuration.isSetWriteConcern()) {
            switch (configuration.getWriteConcern()) {
            case ACKNOWLEDGED:
                database = database.withWriteConcern(ACKNOWLEDGED);
                break;
            case UNACKNOWLEDGED:
                database = database.withWriteConcern(UNACKNOWLEDGED);
                break;
            case JOURNALED:
                database = database.withWriteConcern(JOURNALED);
                break;
            case MAJORITY:
                database = database.withWriteConcern(MAJORITY);
                break;
            case W1:
                database = database.withWriteConcern(W1);
                break;
            case W2:
                database = database.withWriteConcern(W2);
                break;
            case W3:
                database = database.withWriteConcern(W3);
                break;
            }
        }

        collection = database.getCollection(dataset.getCollection());

        if (configuration.isSetWriteConcern()) {
            switch (configuration.getWriteConcern()) {
            case ACKNOWLEDGED:
                collection = collection.withWriteConcern(ACKNOWLEDGED);
                break;
            case UNACKNOWLEDGED:
                collection = collection.withWriteConcern(UNACKNOWLEDGED);
                break;
            case JOURNALED:
                collection = collection.withWriteConcern(JOURNALED);
                break;
            case MAJORITY:
                collection = collection.withWriteConcern(MAJORITY);
                break;
            case W1:
                collection = collection.withWriteConcern(W1);
                break;
            case W2:
                collection = collection.withWriteConcern(W2);
                break;
            case W3:
                collection = collection.withWriteConcern(W3);
                break;
            }
        }

        writeModels = new ArrayList<>();
    }

    private transient List<WriteModel<Document>> writeModels;

    @BeforeGroup
    public void beforeGroup() {
        if (!configuration.isBulkWrite()) {
            return;
        }
        writeModels.clear();
    }

    @AfterGroup
    public void afterGroup() {
        if (!configuration.isBulkWrite()) {
            return;
        }
        if (writeModels != null && !writeModels.isEmpty()) {
            boolean ordered = configuration.getBulkWriteType() == BulkWriteType.ORDERED;
            collection.bulkWrite(writeModels, new BulkWriteOptions().ordered(ordered));
        }
    }

    private class DocumentGenerator {

        private Document document;

        private DocumentGenerator() {
            document = new Document();
        }

        void put(String parentNodePath, String curentName, Object value) {
            if (parentNodePath == null || "".equals(parentNodePath)) {
                document.put(curentName, value);
            } else {
                String objNames[] = parentNodePath.split("\\.");
                Document lastNode = getParentNode(parentNodePath, objNames.length - 1);
                lastNode.put(curentName, value);
                Document parentNode = null;
                for (int i = objNames.length - 1; i >= 0; i--) {
                    parentNode = getParentNode(parentNodePath, i - 1);
                    parentNode.put(objNames[i], lastNode);
                    lastNode = clone(parentNode);
                }
                document = lastNode;
            }
        }

        private Document clone(Document source) {
            Document to = new Document();
            for (java.util.Map.Entry<String, Object> cur : source.entrySet()) {
                to.append(cur.getKey(), cur.getValue());
            }
            return to;
        }

        public Document getParentNode(String parentNodePath, int index) {
            Document parentNode = document;
            if (parentNodePath == null || "".equals(parentNodePath)) {
                return document;
            } else {
                String objNames[] = parentNodePath.split("\\.");
                for (int i = 0; i <= index; i++) {
                    parentNode = (Document) parentNode.get(objNames[i]);
                    if (parentNode == null) {
                        parentNode = new Document();
                        return parentNode;
                    }
                    if (i == index) {
                        break;
                    }
                }
                return parentNode;
            }
        }

        Document getDocument() {
            return this.document;
        }
    }

    @ElementListener
    public void onNext(@Input final Record record) {
        if (configuration.getDataset().getMode() == Mode.TEXT) {
            // we store the whole document here as a string
            String uniqueFieldName = record.getSchema().getEntries().get(0).getName();
            Document document = null;
            try {
                String value = record.getString(uniqueFieldName);
                document = Document.parse(value);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(
                        "the input record is no valid for TEXT mode, the record's first column must be a json format text.");
            }

            doDataAction(record, document);
        } else if (configuration.getDataset().getMode() == Mode.JSON) {
            Document document = convertRecord2DocumentDirectly(record);

            doDataAction(record, document);
        } else {
            /*
             * DocumentGenerator dg = new DocumentGenerator();
             * List<PathMapping> mappings = configuration.getDataset().getPathMappings();
             * Map<String, PathMapping> inputFieldName2PathMapping = new LinkedHashMap<>();
             * 
             * // TODO now only use name mapping, improve it with index mapping
             * for (PathMapping mapping : mappings) {
             * String column = mapping.getColumn();
             * inputFieldName2PathMapping.put(column, mapping);
             * }
             * 
             * for (Schema.Entry entry : record.getSchema().getEntries()) {// schema from input
             * PathMapping mapping = inputFieldName2PathMapping.get(entry.getName());
             * String originElement = mapping.getOriginElement();
             * dg.put(mapping.getParentNodePath(), originElement != null ? originElement : entry.getName(),
             * record.get(Object.class, entry.getName()));
             * }
             * 
             * doDataAction(record, dg.getDocument());
             */
        }
    }

    private void doDataAction(@Input Record record, Document document) {
        switch (configuration.getDataAction()) {
        case INSERT:
            if (configuration.isBulkWrite()) {
                writeModels.add(new InsertOneModel(document));
            } else {
                collection.insertOne(document);
            }
            break;
        case SET:
            if (configuration.isBulkWrite()) {
                if (configuration.isUpdateAllDocuments()) {
                    writeModels
                            .add(new UpdateManyModel<Document>(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document)));
                } else {
                    writeModels
                            .add(new UpdateOneModel<Document>(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document)));
                }
            } else {
                if (configuration.isUpdateAllDocuments()) {
                    collection
                            .updateMany(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document));
                } else {
                    collection
                            .updateOne(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document));
                }
            }
            break;
        case UPSERT_WITH_SET:
            // though mongodb support to set "_id" key self, not auto-generate, but when do upsert with "_id" or other
            // key, it
            // mean if not match, should do insert,
            // but mongo here will throw : Performing an update on the path '_id' would modify the immutable field
            // '_id', i think
            // it's a limit of mongodb as i did't change the value of "_id"
            // why update can works, upsert not work? As not match, should insert, i can't just remove "_id" column to
            // make it
            // right, as that is not expected as lose "_id", and auto-generated when insert.
            // TODO show a more clear exception here
            if (configuration.isBulkWrite()) {
                if (configuration.isUpdateAllDocuments()) {
                    writeModels
                            .add(new UpdateManyModel<Document>(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document), new UpdateOptions().upsert(true)));
                } else {
                    writeModels
                            .add(new UpdateOneModel<Document>(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document), new UpdateOptions().upsert(true)));
                }
            } else {
                if (configuration.isUpdateAllDocuments()) {
                    collection
                            .updateMany(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document), new UpdateOptions().upsert(true));
                } else {
                    collection
                            .updateOne(
                                    getKeysQueryDocumentAndRemoveKeysFromSourceDocument(configuration.getKeyMappings(),
                                            record, document),
                                    new Document("$set", document), new UpdateOptions().upsert(true));
                }
            }
            break;
        }
    }

    @PreDestroy
    public void release() {
        service.closeClient(client);
    }

}
