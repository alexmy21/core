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
package org.lisapark.octopus.core.source.external.impl;

import com.google.common.collect.Maps;
import com.google.gdata.util.ServiceException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.lisapark.octopus.core.Output;
import org.lisapark.octopus.core.ProcessingException;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.event.Attribute;
import org.lisapark.octopus.core.event.Event;
import org.lisapark.octopus.core.event.EventType;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.runtime.ProcessingRuntime;
import org.lisapark.octopus.core.source.external.CompiledExternalSource;
import org.lisapark.octopus.core.source.external.ExternalSource;
import org.lisapark.octopus.util.Booleans;
import org.lisapark.octopus.util.gss.GssListUtils;
import static com.google.common.base.Preconditions.checkState;
import com.google.gson.Gson;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.util.JSON;
import java.net.UnknownHostException;
import java.util.logging.Level;
import org.openide.util.Exceptions;

/**
 *
 * @author Alex Mylnikov (alexmy@lisa-park.com)
 */
public class MongoDbSource extends ExternalSource {

    private static final String DEFAULT_NAME = "MongoDbSource";
    private static final String DEFAULT_DESCRIPTION = "Provides access to Mongo DB source for events";

    private static final String DEFAULT_INPUT = "Input";

    private static final int ATTRIBUTE_URL_PARAMETER_ID = 1;
    private static final String ATTRIBUTE_URL = "MongoDb URL";
    private static final String ATTRIBUTE_URL_DESCRIPTION = "MongoDb URL";

    private static final int ATTRIBUTE_PORT_PARAMETER_ID = 2;
    private static final String ATTRIBUTE_PORT = "MongoDb Port";
    private static final String ATTRIBUTE_PORT_DESCRIPTION = "MongoDb Port.";

    private static final int ATTRIBUTE_DB_PARAMETER_ID = 3;
    private static final String ATTRIBUTE_DB = "Db Name";
    private static final String ATTRIBUTE_DB_DESCRIPTION = "MongoDb database name.";

    private static final int ATTRIBUTE_COL_PARAMETER_ID = 4;
    private static final String ATTRIBUTE_COL = "Collaction";
    private static final String ATTRIBUTE_COL_DESCRIPTION = "MongoDb collection name.";

    private static final int ATTRIBUTE_QUERY_PARAMETER_ID = 5;
    private static final String ATTRIBUTE_QUERY = "Query";
    private static final String ATTRIBUTE_QUERY_DESCRIPTION = "Query Json String.";

    private static final int ATTRIBUTE_FIELDS_PARAMETER_ID = 6;
    private static final String ATTRIBUTE_FIELDS = "Fields";
    private static final String ATTRIBUTE_FIELDS_DESCRIPTION = "List of selected fields (projection).";

    private static final int ATTRIBUTE_SORT_PARAMETER_ID = 7;
    private static final String ATTRIBUTE_SORT = "Sort";
    private static final String ATTRIBUTE_SORT_DESCRIPTION = "Sort Json String.";
    
    private static final int ATTRIBUTE_UID_PARAMETER_ID = 8;
    private static final String ATTRIBUTE_UID = "UID";
    private static final String ATTRIBUTE_UID_DESCRIPTION = "MongoDb User ID";

    private static final int ATTRIBUTE_PWD_PARAMETER_ID = 9;
    private static final String ATTRIBUTE_PWD = "Passwprd";
    private static final String ATTRIBUTE_PWD_DESCRIPTION = "MongoDb User password";


    private final static java.util.logging.Logger logger
            = java.util.logging.Logger.getLogger(GssListSource.class.getName());

    private MongoDbSource(UUID sourceId, String name, String description) {
        super(sourceId, name, description);
    }

    private MongoDbSource(UUID sourceId, MongoDbSource copyFromSource) {
        super(sourceId, copyFromSource);
    }

    private MongoDbSource(MongoDbSource copyFromSource) {
        super(copyFromSource);
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

    private String getQuery() {
        return getParameter(ATTRIBUTE_QUERY_PARAMETER_ID).getValueAsString();
    }

    private String getFields() {
        return getParameter(ATTRIBUTE_FIELDS_PARAMETER_ID).getValueAsString();
    }

    private String getSort() {
        return getParameter(ATTRIBUTE_SORT_PARAMETER_ID).getValueAsString();
    }

    public String getUid() {
        return getParameter(ATTRIBUTE_UID_PARAMETER_ID).getValueAsString();
    }

    public String getPwd() {
        return getParameter(ATTRIBUTE_PWD_PARAMETER_ID).getValueAsString();
    }

    public EventType getEventType() {
        return getOutput().getEventType();
    }

    @Override
    public MongoDbSource newInstance() {
        UUID sourceId = UUID.randomUUID();
        return new MongoDbSource(sourceId, this);
    }

    @Override
    public MongoDbSource copyOf() {
        return new MongoDbSource(this);
    }

    public static MongoDbSource newTemplate() {
        UUID sourceId = UUID.randomUUID();
        MongoDbSource mongoDbSource = new MongoDbSource(sourceId, DEFAULT_NAME, DEFAULT_DESCRIPTION);

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_URL_PARAMETER_ID, ATTRIBUTE_URL)
                .defaultValue("localhost")
                .description(ATTRIBUTE_URL_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.integerParameterWithIdAndName(ATTRIBUTE_PORT_PARAMETER_ID, ATTRIBUTE_PORT)
                .defaultValue(27017)
                .description(ATTRIBUTE_PORT_DESCRIPTION)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_DB_PARAMETER_ID, ATTRIBUTE_DB)
                .defaultValue("GDELT")
                .description(ATTRIBUTE_DB_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_COL_PARAMETER_ID, ATTRIBUTE_COL)
                .defaultValue("gdelt")
                .description(ATTRIBUTE_COL_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_QUERY_PARAMETER_ID, ATTRIBUTE_QUERY)
                .defaultValue("{GlobalEventID:{$in:[288375812,288447227]}}")
                .description(ATTRIBUTE_QUERY_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_FIELDS_PARAMETER_ID, ATTRIBUTE_FIELDS)
                .defaultValue("{GlobalEventID:1}")
                .description(ATTRIBUTE_FIELDS_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_SORT_PARAMETER_ID, ATTRIBUTE_SORT)
                .defaultValue("{GlobalEventID:1}")
                .description(ATTRIBUTE_SORT_DESCRIPTION)
                .required(true)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_UID_PARAMETER_ID, ATTRIBUTE_UID)
                .defaultValue("")
                .description(ATTRIBUTE_UID_DESCRIPTION)
        );

        mongoDbSource.addParameter(
                Parameter.stringParameterWithIdAndName(ATTRIBUTE_PWD_PARAMETER_ID, ATTRIBUTE_PWD)
                .defaultValue("")
                .description(ATTRIBUTE_PWD_DESCRIPTION)
        );
        
        mongoDbSource.setOutput(Output.outputWithId(1).setName("Output"));
        
        return mongoDbSource;
    }

    @Override
    public CompiledExternalSource compile() throws ValidationException {
        validate();

        return new CompiledMongoDbSource(this.copyOf());
    }

    private static class CompiledMongoDbSource implements CompiledExternalSource {

        private final MongoDbSource source;

        private volatile boolean running;
        private Mongo mongoDb = null;

        public CompiledMongoDbSource(MongoDbSource source) {
            this.source = source;
        }

        @Override
        public void startProcessingEvents(ProcessingRuntime runtime) throws ProcessingException {
            // this needs to be atomic, both the check and set
            synchronized (this) {
                checkState(!running, "Source is already processing events. Cannot call processEvents again");
                running = true;
            }

            try {
                String url      = source.getUrl();
                Integer port    = source.getPort();
                String dbName   = source.getDb();
                String collection = source.getCollection();
                String uid      = source.getUid();
                String pwd      = source.getPwd();
                
                String query    = source.getQuery();
                String fields   = source.getFields();
                String sort     = source.getSort();

                if (mongoDb == null) {
                    mongoDb = new Mongo(url, port);
                }

                DB db = mongoDb.getDB(dbName);

                if (!uid.isEmpty() && !db.authenticate(uid, pwd.toCharArray())) {
                    throw new MongoException("Wrong uid or password.");
                }

                DBCollection coll = db.getCollection(collection);

                DBCursor dbResult = getCursor(query, fields, sort, coll);

                processRecord(dbResult, runtime);

            } catch (UnknownHostException ex) {
                Exceptions.printStackTrace(ex);
            } catch (MongoException ex) {
                Exceptions.printStackTrace(ex);
            }

        }

        private DBCursor getCursor(String query, String fields, String sort, DBCollection coll) {
            
            DBObject queryObject;
            DBObject fldObject;
            DBObject sortObject;
            
            DBCursor dbResult;
            
            if(!(query.isEmpty() || fields.isEmpty() || sort.isEmpty())){
                queryObject    = (DBObject) JSON.parse(query);
                fldObject      = (DBObject) JSON.parse(fields);
                sortObject     = (DBObject) JSON.parse(sort);
                
                dbResult = coll.find(queryObject, fldObject).sort(sortObject);
                
            } else if(!(query.isEmpty() || fields.isEmpty())){
                queryObject    = (DBObject) JSON.parse(query);
                fldObject      = (DBObject) JSON.parse(fields);
                
                dbResult = coll.find(queryObject, fldObject);
                
            } else if(!(fields.isEmpty() || sort.isEmpty())){
                queryObject    = (DBObject) JSON.parse("{}");
                fldObject      = (DBObject) JSON.parse(fields);
                sortObject     = (DBObject) JSON.parse(sort);
                
                dbResult = coll.find(queryObject, fldObject).sort(sortObject);
                
            } else if(!(query.isEmpty() || sort.isEmpty())){
                queryObject    = (DBObject) JSON.parse(query);
                sortObject     = (DBObject) JSON.parse(sort);
                
                dbResult = coll.find(queryObject).sort(queryObject);
                
            } else if(!query.isEmpty()){
                queryObject    = (DBObject) JSON.parse(query);
                
                dbResult = coll.find(queryObject);
                
            } else if(!fields.isEmpty()){
                queryObject    = (DBObject) JSON.parse("{}");
                fldObject      = (DBObject) JSON.parse(fields);
                
                dbResult = coll.find(queryObject, fldObject);
                
            } else if(!sort.isEmpty()){
                sortObject     = (DBObject) JSON.parse(sort);
                
                dbResult = coll.find().sort(sortObject);
            } else {
                dbResult = coll.find();
            }
            
            return dbResult;
        }

        void processRecord(DBCursor dbResult, ProcessingRuntime runtime) {
            Thread thread = Thread.currentThread();
            EventType eventType = source.getEventType();

            while (dbResult.hasNext() && !thread.isInterrupted() && running) {
                DBObject item = dbResult.next();
                Map<String, Object> map = new Gson().fromJson(JSON.serialize(item), Map.class);

                Event newEvent = createEventFromRecord(map, eventType);

                runtime.sendEventFromSource(newEvent, source);
                System.out.println(map);
            }

        }

        @Override
        public void stopProcessingEvents() {
            this.running = false;
        }

        Event createEventFromRecord(Map<String, Object> record, EventType eventType) {
            Map<String, Object> attributeValues = Maps.newHashMap();

            for (Attribute attribute : eventType.getAttributes()) {
                Class type = attribute.getType();
                String attributeName = attribute.getName().trim();
                Object objValue = record.get(attributeName);

                if (type == String.class) {
                    String value = "";
                    if (objValue != null) {
                        value = (String) objValue;
                    }
                    attributeValues.put(attributeName, value);

                } else if (type == Integer.class) {
                    int value = 0;
                    if (objValue != null && !objValue.toString().trim().isEmpty()) {
                        if (objValue instanceof Double) {
                            value = (int) (Double.parseDouble(objValue.toString()));
                        } else if (objValue instanceof Float) {
                            value = (int) (Float.parseFloat(objValue.toString()));
                        } else {
                            value = Integer.parseInt(objValue.toString());
                        }
                    }
                    attributeValues.put(attributeName, value);

                } else if (type == Short.class) {
                    Short value = 0;
                    if (objValue != null && !objValue.toString().trim().isEmpty()) {
                        value = Short.parseShort(objValue.toString());
                    }
//                    short value = Short.parseShort((String)cellRange.get(count).get(attributeName));
                    attributeValues.put(attributeName, value);

                } else if (type == Long.class) {
                    Long value = 0L;
                    if (objValue != null && !objValue.toString().trim().isEmpty()) {
                        value = Long.parseLong(objValue.toString());
                    }
//                    long value = Long.parseLong((String)cellRange.get(count).get(attributeName));
                    attributeValues.put(attributeName, value);

                } else if (type == Double.class) {
                    Double value = 0D;
                    if (objValue != null && !objValue.toString().trim().isEmpty()) {
                        value = Double.parseDouble(objValue.toString());
                    }
//                    double value = Double.parseDouble((String)cellRange.get(count).get(attributeName));
                    attributeValues.put(attributeName, value);

                } else if (type == Float.class) {
                    Float value = 0F;
                    if (objValue != null && !objValue.toString().trim().isEmpty()) {
                        value = Float.parseFloat(objValue.toString());
                    }
//                    float value = Float.parseFloat((String)cellRange.get(count).get(attributeName));
                    attributeValues.put(attributeName, value);

                } else if (type == Boolean.class) {
                    String value = (String) record.get(attributeName);
                    attributeValues.put(attributeName, Booleans.parseBoolean(value));
                } else {
                    throw new IllegalArgumentException(String.format("Unknown attribute type %s", type));
                }
            }

            return new Event(attributeValues);
        }

    }
}
