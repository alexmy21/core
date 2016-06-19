/* 
 * Copyright (C) 2013 Lisa Park, Inc. (www.lisa-park.net)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.lisapark.octopus.core.sink.external.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.util.JSON;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.json.JSONObject;
import org.lisapark.octopus.core.AbstractNode;
import org.lisapark.octopus.core.Input;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.event.Event;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.runtime.SinkContext;
import org.lisapark.octopus.core.sink.external.CompiledExternalSink;
import org.lisapark.octopus.core.sink.external.ExternalSink;
import org.lisapark.octopus.core.source.Source;
import org.openide.util.Exceptions;

/**
 *
 * @author Alex Mylnikov (alexmy@lisa-park.com)
 */
public class MongoDbSink extends AbstractNode implements ExternalSink {

    private static final String DEFAULT_NAME = "MongoDb";
    private static final String DEFAULT_DESCRIPTION = "MongoDb Sink";
    private static final String DEFAULT_INPUT = "Input";

    private static final int ATTRIBUTE_URL_PARAMETER_ID = 1;
    private static final String ATTRIBUTE_URL = "MongoDb URL";
    private static final String ATTRIBUTE_URL_DESCRIPTION = "MongoDb URL";

    private static final int ATTRIBUTE_PORT_PARAMETER_ID = 2;
    private static final String ATTRIBUTE_PORT = "MongoDb Port";
    private static final String ATTRIBUTE_PORT_DESCRIPTION = "MongoDb Port";

    private static final int ATTRIBUTE_DB_PARAMETER_ID = 3;
    private static final String ATTRIBUTE_DB = "Db Name";
    private static final String ATTRIBUTE_DB_DESCRIPTION = "MongoDb database name";

    private static final int ATTRIBUTE_COL_PARAMETER_ID = 4;
    private static final String ATTRIBUTE_COL = "Collaction";
    private static final String ATTRIBUTE_COL_DESCRIPTION = "MongoDb collection name";

    private static final int ATTRIBUTE_INDEX_PARAMETER_ID = 5;
    private static final String ATTRIBUTE_INDEX = "Index Field";
    private static final String ATTRIBUTE_INDEX_DESCRIPTION = "MongoDb index field name";

    private static final int ATTRIBUTE_UID_PARAMETER_ID = 6;
    private static final String ATTRIBUTE_UID = "UID";
    private static final String ATTRIBUTE_UID_DESCRIPTION = "MongoDb User ID";

    private static final int ATTRIBUTE_PWD_PARAMETER_ID = 7;
    private static final String ATTRIBUTE_PWD = "Passwprd";
    private static final String ATTRIBUTE_PWD_DESCRIPTION = "MongoDb User password";

    private Input<Event> input;
    private CompiledMongoDbSink compiledMongoDbSink;

    private MongoDbSink(UUID id, String name, String description) {
        super(id, name, description);
        input = Input.eventInputWithId(1);
        input.setName(DEFAULT_INPUT);
        input.setDescription(DEFAULT_INPUT);
    }

    private MongoDbSink(UUID id, MongoDbSink copyFromNode) {
        super(id, copyFromNode);
        input = copyFromNode.getInput().copyOf();
    }

    private MongoDbSink(MongoDbSink copyFromNode) {
        super(copyFromNode);
        this.input = copyFromNode.input.copyOf();
    }

    public String getUrl() {
        return getParameter(ATTRIBUTE_URL_PARAMETER_ID).getValueAsString();
    }

    public Integer getPort() {
        return getParameter(ATTRIBUTE_PORT_PARAMETER_ID).getValueAsInteger();
    }

    public String getDb() {
        return getParameter(ATTRIBUTE_DB_PARAMETER_ID).getValueAsString();
    }

    public String getCollection() {
        return getParameter(ATTRIBUTE_COL_PARAMETER_ID).getValueAsString();
    }

    private String getIndName() {
        return getParameter(ATTRIBUTE_INDEX_PARAMETER_ID).getValueAsString();
    }

    public String getUid() {
        return getParameter(ATTRIBUTE_UID_PARAMETER_ID).getValueAsString();
    }

    public String getPwd() {
        return getParameter(ATTRIBUTE_PWD_PARAMETER_ID).getValueAsString();
    }

    public Input<Event> getInput() {
        return input;
    }

    @Override
    public List<Input<Event>> getInputs() {
        return ImmutableList.of(input);
    }

    @Override
    public boolean isConnectedTo(Source source) {

        return input.isConnectedTo(source);
    }

    @Override
    public void disconnect(Source source) {
        if (input.isConnectedTo(source)) {
            input.clearSource();
        }
    }

    @Override
    public MongoDbSink newInstance() {
        return new MongoDbSink(UUID.randomUUID(), this);
    }

    @Override
    public MongoDbSink copyOf() {
        return new MongoDbSink(this);
    }

    public static MongoDbSink newTemplate() {
        UUID sinkId = UUID.randomUUID();
        MongoDbSink mongoDbSink = new MongoDbSink(sinkId, DEFAULT_NAME, DEFAULT_DESCRIPTION);

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_URL_PARAMETER_ID, ATTRIBUTE_URL)
                .defaultValue("localhost")
                .description(ATTRIBUTE_URL_DESCRIPTION)
                .required(true)
        );

        mongoDbSink.addParameter(
                Parameter.integerParameterWithIdAndName(ATTRIBUTE_PORT_PARAMETER_ID, ATTRIBUTE_PORT)
                .defaultValue(27017)
                .description(ATTRIBUTE_PORT_DESCRIPTION)
        );

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_DB_PARAMETER_ID, ATTRIBUTE_DB)
                .defaultValue("GDELT")
                .description(ATTRIBUTE_DB_DESCRIPTION)
                .required(true)
        );

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_COL_PARAMETER_ID, ATTRIBUTE_COL)
                .defaultValue("gdelt")
                .description(ATTRIBUTE_COL_DESCRIPTION)
                .required(true)
        );

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_INDEX_PARAMETER_ID, ATTRIBUTE_INDEX)
                .defaultValue("GlobalEventID")
                .description(ATTRIBUTE_INDEX_DESCRIPTION)
                .required(true)
        );

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_UID_PARAMETER_ID, ATTRIBUTE_UID)
                .defaultValue("")
                .description(ATTRIBUTE_UID_DESCRIPTION)
        );

        mongoDbSink.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_PWD_PARAMETER_ID, ATTRIBUTE_PWD)
                .defaultValue("")
                .description(ATTRIBUTE_PWD_DESCRIPTION)
        );

        return mongoDbSink;
    }

    @Override
    public CompiledExternalSink compile() throws ValidationException {
        compiledMongoDbSink = new CompiledMongoDbSink(copyOf());
        return compiledMongoDbSink;
    }

    static class CompiledMongoDbSink extends CompiledExternalSink {

        private final MongoDbSink mongoDbSink;
        private Mongo mongoDb = null;
        private DBObject fnObject = null;

        private int count = 0;

        protected CompiledMongoDbSink(MongoDbSink processor) {
            super(processor);
            this.mongoDbSink = processor;
        }

        @Override
        public synchronized void processEvent(SinkContext ctx, Map<Integer, Event> eventsByInputId) {
            Event event = eventsByInputId.get(1);
            if (event != null) {

                Map<String, Object> data = eventsByInputId.get(1).getData();

                try {
                    String url = mongoDbSink.getUrl();
                    Integer port = mongoDbSink.getPort();
                    String dbName = mongoDbSink.getDb();
                    String collection = mongoDbSink.getCollection();
                    String indName = mongoDbSink.getIndName();
                    String uid = mongoDbSink.getUid();
                    String pwd = mongoDbSink.getPwd();

                    if (mongoDb == null) {
                        mongoDb = new Mongo(url, port);
                    }

                    DB db = mongoDb.getDB(dbName);

                    if (!uid.isEmpty() && !db.authenticate(uid, pwd.toCharArray())) {
                        throw new MongoException("Wrong uid or password.");
                    }

                    // DBObject to search for duplicate records
                    if (fnObject == null) {
                        fnObject = (DBObject) JSON.parse(getIndexJson(indName));
                    }

                    DBCollection coll = db.getCollection(collection);

                    coll.ensureIndex(fnObject);
//                    String jsonObject = new Gson().toJson(data, Map.class);
                    DBObject dbObject = buidDBObject(data);

                    coll.insert(dbObject, WriteConcern.NONE);

                } catch (UnknownHostException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (MongoException ex) {
                    Exceptions.printStackTrace(ex);
                }

            } else {
                ctx.getStandardOut().println("event is null");
            }
        }

        private String getIndexJson(String indName) {
            Map<String, Object> map = Maps.newHashMap();

            map.put(indName, 1);
            map.put("unique", Boolean.TRUE);
            String jsonObject = new Gson().toJson(map, Map.class);

            return jsonObject;
        }

        private DBObject buidDBObject(Map<String, Object> data) {
            BasicDBObject document = new BasicDBObject();
            for (Entry<String, Object> entry : data.entrySet()) {
                if (entry.getValue() != null) {
                    document = addEntry(document, entry);
                }
            }

            return document;
        }

        private BasicDBObject addEntry(BasicDBObject document, Entry<String, Object> entry) {
            if (entry.getValue() instanceof Integer) {
                document.append(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                document.append(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof Double) {
                document.append(entry.getKey(), (Double) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                document.append(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                document.append(entry.getKey(), (Boolean) entry.getValue());
            } else {
                document.append(entry.getKey(), entry.getValue().toString());
            }

            return document;
        }
    }

    /**
     * Release mongo db resources, this method called from ModelRunner
     */
    @Override
    public void complete() {
        if (compiledMongoDbSink.mongoDb != null) {
            compiledMongoDbSink.mongoDb.close();
        }
    }
}
