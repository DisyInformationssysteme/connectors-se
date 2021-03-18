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
package org.talend.components.mongodb.source;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.common.stream.input.json.JsonToRecord;
import org.talend.components.mongo.dataset.MongoCommonDataSet;
import org.talend.components.mongo.datastore.MongoCommonDataStore;
import org.talend.components.mongo.service.DocumentToRecord;
import org.talend.components.mongo.source.MongoCommonInput;
import org.talend.components.mongo.source.MongoCommonSourceConfiguration;
import org.talend.components.mongodb.dataset.MongoDBReadDataSet;
import org.talend.components.mongodb.service.I18nMessage;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Iterator;

@Slf4j
@Documentation("This component reads data from MongoDB.")
public class MongoDBReader extends MongoCommonInput {

    private I18nMessage i18n;

    private static final transient Logger LOG = LoggerFactory.getLogger(MongoDBReader.class);

    private final MongoCommonSourceConfiguration configuration;

    private MongoDBService service;

    private transient MongoClient client;

    private transient String query4Split;

    public MongoDBReader(@Option("configuration") final MongoCommonSourceConfiguration configuration,
            final MongoDBService service,
            final RecordBuilderFactory builderFactory, final I18nMessage i18n, String query4Split) {
        super.configuration = this.configuration = configuration;
        super.service = this.service = service;
        this.builderFactory = builderFactory;
        this.i18n = i18n;
        this.query4Split = query4Split;
    }

    Iterator<Document> iterator = null;

    @PostConstruct
    public void init() {
        jsonToRecord = new JsonToRecord(this.builderFactory);
        documentToRecord = new DocumentToRecord(this.builderFactory);

        MongoCommonDataSet dataset = configuration.getDataset();
        MongoCommonDataStore datastore = dataset.getDatastore();
        client = service.createClient(datastore);
        MongoDatabase database = client.getDatabase(datastore.getDatabase());
        MongoCollection<Document> collection = database.getCollection(dataset.getCollection());

        iterator = fetchData(dataset, collection);
    }

    private Iterator<Document> fetchData(MongoCommonDataSet dataset, MongoCollection<Document> collection) {
        if (query4Split != null) {
            log.info("query for mongodb split : " + query4Split);
            return collection.find(service.getBsonDocument(query4Split)).iterator();
        }

        Long sampleLimit = configuration.getSampleLimit();
        if (dataset instanceof MongoDBReadDataSet) {
            // return fetchData((MongoDBReadDataSet) dataset, collection);
            BsonDocument query = service.getBsonDocument(((MongoDBReadDataSet) dataset).getQuery());
            FindIterable<Document> fi = collection.find(query);
            if (sampleLimit != null && sampleLimit > 0) {
                fi = fi.limit(sampleLimit.intValue());
            }
            return fi.iterator();
        } else {
            FindIterable<Document> fi = collection.find();
            if (sampleLimit != null && sampleLimit > 0) {
                fi = fi.limit(sampleLimit.intValue());
            }
            return fi.iterator();
        }
    }

    /*
     * private Iterator<Document> fetchData(MongoDBReadDataSet dataset, MongoCollection<Document> collection) {
     * Iterable iterable = null;
     * switch (dataset.getQueryType()) {
     * case FIND:
     * BsonDocument query = service.getBsonDocument(dataset.getQuery());
     * BsonDocument projection = service.getBsonDocument(dataset.getProjection());
     * // FindIterable<Document>
     * int limit = dataset.getLimit();
     * FindIterable ft = collection.find(query).projection(projection);
     * if (limit > 0) {
     * iterable = ft.limit(limit);
     * } else {
     * iterable = ft;
     * }
     * break;
     * case AGGREGATION:
     * List<BsonDocument> aggregationStages = new ArrayList<>();
     * for (AggregationStage stage : dataset.getAggregationStages()) {
     * aggregationStages.add(service.getBsonDocument(stage.getStage()));
     * }
     * // AggregateIterable<Document>
     * iterable = collection.aggregate(aggregationStages).allowDiskUse(dataset.isEnableExternalSort());
     * break;
     * default:
     * break;
     * }
     * 
     * return iterable.iterator();
     * }
     */

    @Producer
    public Record next() {
        if (iterator.hasNext()) {
            Document document = iterator.next();
            return doConvert(document);
        }
        return null;
    }

    /*
     * private List<PathMapping> initPathMappings(Document document) {
     * List<PathMapping> pathMappings = configuration.getDataset().getPathMappings();
     * if (pathMappings == null || pathMappings.isEmpty()) {
     * return service.guessPathMappingsFromDocument(document);
     * }
     * return pathMappings;
     * }
     * 
     * // only create schema by first document and path mapping
     * private transient Schema schema;
     * 
     * private Record toFlatRecordWithMapping(Document document) {
     * List<PathMapping> pathMappings = initPathMappings(document);
     * if (schema == null) {
     * schema = service.createSchema(document, pathMappings);
     * }
     * final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schema);
     * Iterator<Schema.Entry> entries = schema.getEntries().iterator();
     * for (PathMapping mapping : pathMappings) {
     * // column for flow struct
     * String column = mapping.getColumn();
     * // the mongodb's origin element name in bson
     * String originElement = mapping.getOriginElement();
     * // path to locate the parent element of value provider of bson object
     * String parentNodePath = mapping.getParentNodePath();
     * Object value = service.getValueByPathFromDocument(document, parentNodePath, originElement);
     * 
     * Schema.Entry entry = entries.next();
     * 
     * addColumn(recordBuilder, entry, value);
     * }
     * return recordBuilder.build();
     * }
     */

    @PreDestroy
    public void release() {
        service.closeClient(client);
    }

}