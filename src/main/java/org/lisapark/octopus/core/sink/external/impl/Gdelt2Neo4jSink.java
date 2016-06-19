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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.lisapark.octopus.core.AbstractNode;
import org.lisapark.octopus.core.Input;
import org.lisapark.octopus.core.Persistable;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.event.Event;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.runtime.SinkContext;
import org.lisapark.octopus.core.sink.external.CompiledExternalSink;
import org.lisapark.octopus.core.sink.external.ExternalSink;
import org.lisapark.octopus.core.source.Source;
import org.lisapark.octopus.util.neo4j.GdeltNeo4jUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dave sinclair(david.sinclair@lisa-park.com)
 */
@Persistable
public class Gdelt2Neo4jSink extends AbstractNode implements ExternalSink {
    
    static final Logger LOG = LoggerFactory.getLogger(Gdelt2Neo4jSink.class);
    
    private static final String DEFAULT_NAME = "GDELT to Neo4j";
    private static final String DEFAULT_DESCRIPTION = "Adds GDELT data as an event"
            + " to the Neo4j Graph database.";
    private static final String DEFAULT_INPUT = "Input";    
    
    private static final int NEO4J_URL_PARAMETER_ID       = 1;
    private static final String NEO4J_URL                 = "URL:";
    private static final String NEO4J_URL_DESCRIPTION     = "Neo4j Server URL.";
    
    private static final int USER_ID_PARAMETER_ID         = 2;
    private static final String USER_ID                   = "User ID:";
    private static final String USER_ID_DESCRIPTION       = "User ID.";
    
    private static final int PASSWORD_PARAMETER_ID        = 3;
    private static final String PASSWORD                  = "Password:";
    private static final String PASSWORD_DESCRIPTION      = "Password.";
    
    private static final int QUERY_CLUSTER_PARAMETER_ID   = 4;
    private static final String QUERY_CLUSTER             = "Cluster Attribute";
    private static final String QUERY_CLUSTER_DESCRIPTION = "Attribute name"
            + " associated with the MongoDB query.";
    
    private static final int QUERY_LEAVES_PARAMETER_ID    = 5;
    private static final String QUERY_LEAVES              = "Query leaves";
    private static final String QUERY_LEAVES_DESCRIPTION  = "List of comma separated"
            + " attribute names that are associated with the query cluster."
            + " They should be in the attribute list of the model Source Processor."
            + " Empty - means that leaves will be represented by"
            + " all attributes defined in the model (Source processor attributes"
            + " plus all attributes generated in the process of model execution.";
    
    private Input<Event> input;

    private Gdelt2Neo4jSink(UUID id, String name, String description) {
        super(id, name, description);
        input = Input.eventInputWithId(1);
        input.setName(DEFAULT_INPUT);
        input.setDescription(DEFAULT_INPUT);
    }

    private Gdelt2Neo4jSink(UUID id, Gdelt2Neo4jSink copyFromNode) {
        super(id, copyFromNode);
        input = copyFromNode.getInput().copyOf();
    }

    private Gdelt2Neo4jSink(Gdelt2Neo4jSink copyFromNode) {
        super(copyFromNode);
        this.input = copyFromNode.input.copyOf();
    }
    
    public String getNeo4jUrl() {
        return getParameter(NEO4J_URL_PARAMETER_ID).getValueAsString();
    }
    
    public String getUserId() {
        return getParameter(USER_ID_PARAMETER_ID).getValueAsString();
    }
    
    public String getPassword() {
        return getParameter(PASSWORD_PARAMETER_ID).getValueAsString();
    }
    
    /**
     * 
     * @return 
     */
    private String getLeaves() {
        return getParameter(QUERY_LEAVES_PARAMETER_ID).getValueAsString();
    }

    /**
     * 
     * @return 
     */
    private String getQuery() {
        return getParameter(QUERY_CLUSTER_PARAMETER_ID).getValueAsString();
    }

    /**
     * 
     * @return 
     */
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
    public Gdelt2Neo4jSink newInstance() {
        return new Gdelt2Neo4jSink(UUID.randomUUID(), this);
    }

    @Override
    public Gdelt2Neo4jSink copyOf() {
        return new Gdelt2Neo4jSink(this);
    }

    public static Gdelt2Neo4jSink newTemplate() {
        UUID sinkId = UUID.randomUUID();
        
        Gdelt2Neo4jSink neo4jSink = new Gdelt2Neo4jSink(sinkId, DEFAULT_NAME, DEFAULT_DESCRIPTION);
        
        neo4jSink.addParameter(
                Parameter.stringParameterWithIdAndName(NEO4J_URL_PARAMETER_ID, NEO4J_URL)
                .defaultValue("http://54.237.167.60:7474/db/data/")
                .description(NEO4J_URL_DESCRIPTION)
                );
        neo4jSink.addParameter(
                Parameter.stringParameterWithIdAndName(USER_ID_PARAMETER_ID, USER_ID)
                .defaultValue("")
                .description(USER_ID_DESCRIPTION)
                );
        neo4jSink.addParameter(
                Parameter.stringParameterWithIdAndName(PASSWORD_PARAMETER_ID, PASSWORD)
                .defaultValue("")
                .description(PASSWORD_DESCRIPTION)
                );
        neo4jSink.addParameter(
                Parameter.stringParameterWithIdAndName(QUERY_CLUSTER_PARAMETER_ID, QUERY_CLUSTER)
                .defaultValue("")
                .required(true)
                .description(QUERY_CLUSTER_DESCRIPTION)
        );
        neo4jSink.addParameter(
                Parameter.stringParameterWithIdAndName(QUERY_LEAVES_PARAMETER_ID, QUERY_LEAVES)
                .defaultValue("")
                .description(QUERY_LEAVES_DESCRIPTION)
                );
        
        return neo4jSink;
    }

    @Override
    public CompiledExternalSink compile() throws ValidationException {
        return new CompiledModel2Neo4jSink(copyOf());
    }

    static class CompiledModel2Neo4jSink extends CompiledExternalSink {
        
        private final Gdelt2Neo4jSink sink;
        private GraphDatabaseService graphDb = null; 
        private GdeltNeo4jUtils utils;
        
//        private Node queryNode = null;
                
        protected CompiledModel2Neo4jSink(Gdelt2Neo4jSink processor) {
            super(processor);
            this.sink = processor;
            
        }

        @Override
        public synchronized void processEvent(SinkContext ctx, Map<Integer, Event> eventsByInputId) {
            Event event = eventsByInputId.get(1);
            
            if (utils == null) {
                this.utils = new GdeltNeo4jUtils();
            }

            if (utils.getGraphDbService() == null) {
                utils.setGraphDbService(utils.newServerInstance(sink.getNeo4jUrl()));
            }
  
            graphDb = utils.getGraphDbService();
            
            LOG.info("Event: " + event);
            
            Map<String, Object> data = event.getData();
            String query = sink.getQuery();
            String leaves = sink.getLeaves();
            
            LOG.info("Model JSON: " + query);            
            
            if (graphDb != null && !query.trim().isEmpty()) {               
                Node eventNode = utils.addGdeltEvent(query, leaves, data, graphDb);
                Node queryNode = utils.addMongoQuery(query, leaves, graphDb);
                utils.addEventRelation(queryNode, eventNode, graphDb);
                utils.addQueryRelation(queryNode, graphDb);
                
                ctx.getStandardOut().println(query);
            } else {
                ctx.getStandardOut().println("event is null or empty");
            }
        } 
        
        @Override
        protected void finalize() throws Throwable{
            utils.getGraphDbService().shutdown();            
            super.finalize();            
        }
    }
}
