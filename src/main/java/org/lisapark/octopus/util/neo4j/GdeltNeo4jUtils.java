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
package org.lisapark.octopus.util.neo4j;

import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.util.Arrays;
import java.util.Map;
import org.lisapark.octopus.repository.RepositoryException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.Uniqueness;
import org.neo4j.rest.graphdb.RestGraphDatabase;

/**
 *
 * @author alexmy
 */
public class GdeltNeo4jUtils {

    private static final String NODE_TYPE   = "NodeType";
    private static final String QUERY       = "query";
    private static final String EVENT       = "event";
    private static final String ROOT        = "root";
    
    private static final String EVENT_INDEX = "EventIndex";
    private static final String QUERY_INDEX = "QueryIndex";

    private static final String QUERY_ID = "QueryId";
    private static final String QUERY_NAME = "QueryName";
    private static final String QUERY_RELS = "QueryRels";

    private static final String EVENT_ID = "GlobalEventID";
    private static final String EVENT_NAME = "EventName";
    private static final String EVENT_RELS = "EventRels";

    private static final String CLUSTER_RELS = "ClusterRels";
    private static final String REL_NAME = "RelName";

    private static final String CLUSTER_INDEX = "ClusterIndex";
    private static final String CLUSTER_NAME = "ClusterName";
    private static final String ROOT_CLUSTER = "GDELT";

    public static enum RelTypes implements RelationshipType {

        MODEL,      // Relations between model node and it's parts (sources, processors, sinks)
        ATTRIBUTE,  // Attribute based relations between cluster and it's models
        VALUE,      // Value based relations between cluster and it's models
        CONTEXT,    // Context formation relations
        CLUSTER,    // Cluster relation
        QUERY,      // Query relation 
        EVENT       //Event relation
    }

    private RestGraphDatabase graphDb;

    private GraphDatabaseService graphDbService = null;
    private static final String SERVER_ROOT_URI = "http://54.237.167.60:7474/db/data/";

    //==========================================================================
    // New code for Neo4j 2.x
    //==========================================================================
    
    /**
     * 
     * @param query
     * @param leaves
     * @param data
     * @param graphDb
     * @return 
     */
    public synchronized Node addGdeltEvent(String query,
            String leaves, Map<String, Object> data, GraphDatabaseService graphDb) {

        Node eventNode = null;

        Integer eventId = ensureInteger(data.get(EVENT_ID));

        if (eventId != null) {
            IndexManager index = graphDb.index();
            Index<Node> events = index.forNodes(EVENT_INDEX);
            IndexHits<Node> hits = events.get(EVENT_NAME, eventId);

            String[] attributes = leaves.replace(" ", "").split(",");
            Arrays.sort(attributes);

            if (hits.size() == 0) {

                Transaction tx = graphDb.beginTx();

                try {
                    eventNode = graphDb.createNode();
                    eventNode.setProperty(NODE_TYPE, EVENT);

                    for (String attribute : attributes) {
                        Object value = data.get(attribute);
                        if (value != null) {
                            eventNode.setProperty(attribute, value);
                        }
                    }

                    // Ensure that event id property is in the node props list
                    Object eventIdProp = eventNode.getProperty(EVENT_ID);
                    if (eventIdProp == null) {
                        eventNode.setProperty(EVENT_ID, eventId);
                    }

                    events.add(eventNode, EVENT_NAME, eventId);

                    tx.success();
                } finally {
                    tx.finish();
                }
            } else {
                eventNode = hits.getSingle();
            }
        }

        return eventNode;
    }

    /**
     * 
     * @param query
     * @param leaves
     * @param graphDb
     * @return 
     */
    public Node addMongoQuery(String query, String leaves, GraphDatabaseService graphDb) {

        Node queryNode = null;

        String[] attributes = leaves.replace(" ", "").split(",");
        Arrays.sort(attributes);
        
        String queryName = createQueryName(query, attributes);

        IndexManager index = graphDb.index();
        Index<Node> queries = index.forNodes(QUERY_INDEX);
        IndexHits<Node> hits = queries.get(QUERY_NAME, queryName);

        if (hits.size() == 0) {

            Transaction tx = graphDb.beginTx();

            try {
                queryNode = graphDb.createNode();
                queryNode.setProperty(NODE_TYPE, QUERY);
                queryNode.setProperty(QUERY_ID, queryName);
                queries.add(queryNode, QUERY_NAME, queryName);

                tx.success();
            } finally {
                tx.finish();
            }
        } else {
            queryNode = hits.getSingle();
        }

        return queryNode;
    }

    /**
     *
     * @param query
     * @param graphDb
     * @throws JsonSyntaxException
     */
    public synchronized void addQueryRelation(Node query, GraphDatabaseService graphDb) {
        
        // First check if GDELT Root node is there
        IndexManager rootIndex = graphDb.index();
        Index<Node> roots = rootIndex.forNodes(CLUSTER_INDEX);
        IndexHits<Node> rootHits = roots.get(CLUSTER_NAME, ROOT_CLUSTER);

        // Create or get root node
        Node root;
        if (rootHits.size() == 0) {
            root = graphDb.createNode();
            root.setProperty(NODE_TYPE, ROOT);
            root.setProperty(CLUSTER_NAME, ROOT_CLUSTER);
            roots.add(root, CLUSTER_NAME, ROOT_CLUSTER);
        } else {
            root = rootHits.getSingle();
        }

        IndexManager relIndex = graphDb.index();
        Index<Relationship> queryRels = relIndex.forRelationships(QUERY_RELS);

        // Check if relationship is in the index and add it, if it is not there yet
        String queryName = (String) query.getProperty(QUERY_ID);

        int hitSize;
        if (queryName != null) {
            IndexHits<Relationship> relHits = queryRels.get(QUERY_NAME, queryName);
            hitSize = relHits.size();
            if (hitSize == 0) {
                Transaction tx = graphDb.beginTx();
                try {
                    Relationship rel = query.createRelationshipTo(root, RelTypes.QUERY);
                    rel.setProperty(REL_NAME, queryName);
                    queryRels.add(rel, REL_NAME, queryName);
                    
                    tx.success();
                } finally {
                    tx.finish();
                }
            }
        }
    }

    /**
     * 
     * @param query
     * @param event
     * @param graphDb 
     */
    public synchronized void addEventRelation(Node query, Node event, GraphDatabaseService graphDb) {

        IndexManager relIndex = graphDb.index();
        Index<Relationship> eventRels = relIndex.forRelationships(EVENT_RELS);

        // Check if relationship is in the index and add it, if it is not there yet
        Object eventNameObj = event.getProperty(EVENT_ID);
        Integer eventName = ensureInteger(eventNameObj);

        int hitSize = 0;
        if (eventName != null) {
            IndexHits<Relationship> relHits = eventRels.get(EVENT_NAME, eventName);
            hitSize = relHits.size();
        }

        if (hitSize == 0 && eventName != null) {
            Transaction tx = graphDb.beginTx();
            try {
                Relationship rel = event.createRelationshipTo(query, RelTypes.EVENT);
                rel.setProperty(REL_NAME, eventName);
                eventRels.add(rel, REL_NAME, eventName);
                tx.success();
            } finally {
                tx.finish();
            }
        }
    }

    //==========================================================================
    private Integer ensureInteger(Object objValue) {

        Integer value = null;

        if (objValue != null && !objValue.toString()
                .trim().isEmpty()) {
            if (objValue instanceof Double) {
                value = (int) (Double.parseDouble(objValue.toString()));
            } else if (objValue instanceof Float) {
                value = (int) (Float.parseFloat(objValue.toString()));
            } else {
                value = Integer.parseInt(objValue.toString());
            }
        }
        return value;
    }
    
    private String createQueryName(String query, String[] attributes) {
        StringBuilder queryName = new StringBuilder();
        
        queryName.append(query);
        for(String attribute: attributes){            
            if(!query.equalsIgnoreCase(attribute)){
                queryName.append("|").append(attribute);
            }            
        }
        
        return queryName.toString();
    }


    // Set of traversal descriptions
    //==========================================================================
    final TraversalDescription SINKS_TRAVERSAL = Traversal.description()
            .breadthFirst()
            .relationships(GdeltNeo4jUtils.RelTypes.MODEL)
            .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL);


    /**
     *
     * @param args
     * @throws RepositoryException
     */
    public static void main(String[] args) throws RepositoryException {

        GdeltNeo4jUtils utils = new GdeltNeo4jUtils();

        if (utils.getGraphDbService() == null) {
            utils.setGraphDbService(utils.newServerInstance(SERVER_ROOT_URI));
        }

        Runtime.getRuntime().exit(0);
    }

    /**
     *
     * @param neo4jUrl
     * @return
     */
    public synchronized GraphDatabaseService newEmbeddedInstance(String neo4jUrl) {
        return new GraphDatabaseFactory().newEmbeddedDatabase(neo4jUrl);
    }

    /**
     *
     * @param neo4jUrl
     * @return
     */
    public synchronized GraphDatabaseService newServerInstance(String neo4jUrl) {

        int status = checkDatabaseIsRunning(neo4jUrl);

        if (status == 200) {
            return new RestGraphDatabase(neo4jUrl);
        } else {
            return null;
        }
    }

    /**
     *
     * @return
     */
    public GraphDatabaseService getGraphDbService() {
        return graphDbService;
    }

    /**
     *
     * @param graphDbService
     */
    public void setGraphDbService(GraphDatabaseService graphDbService) {
        this.graphDbService = graphDbService;
    }

    /**
     *
     * @return
     */
    public RestGraphDatabase getGraphDb() {
        return graphDb;
    }

    /**
     *
     * @param graphDb
     */
    public void setGraphDb(RestGraphDatabase graphDb) {
        this.graphDb = graphDb;
    }

    /**
     *
     * @param server_uri
     * @return
     */
    public int checkDatabaseIsRunning(String server_uri) {

        // START SNIPPET: checkServer
        WebResource resource = Client.create().resource(server_uri);
        ClientResponse response = resource.get(ClientResponse.class);
        int status = response.getStatus();

        System.out.println(String.format("GET on [%s], status code [%d]",
                server_uri, status));

        response.close();

        // END SNIPPET: checkServer
        return status;
    }
}
