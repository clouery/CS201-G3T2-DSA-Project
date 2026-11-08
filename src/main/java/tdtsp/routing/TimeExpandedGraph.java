package tdtsp.routing;

import java.util.ArrayList;
import java.util.List;

public class TimeExpandedGraph {
    
    private final Connection[] connections;
    private final int numStops;
    private final int transferTimeMin;

    // In a Time Expanded Graph, each event (e.g., a  train arrival/departure) is its own node
    static class EventNode {
        int tripId;
        int stopId;
        int time;
        EventType type;
    }

    enum EventType {
        ARRIVAL,
        DEPARTURE
    }

    // Each Edge connects EventNodes and represents either travel, transfer, or wait time at a station
    record Edge(int to, int weight){};

    // A list that stores all EventNodes
    private final List<EventNode> nodes;

    // We use an Adjacency List to store the list of outgoing Edges for each EventNode
    private final List<List<Edge>> adjacencyList;

    public int numNodes() {
        return nodes.size();
    }

    public int numArcs() {
        int count = 0;

        for (List<Edge> edges : adjacencyList) {
            count += edges.size();
        }

        return count;
    }

    /**
     * Retrieve an EventNode
     * @param nodeId
     * @return
     */
    public EventNode getNode(int nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Get outgoing Edges from an EventNode
     * @param nodeId
     * @return
     */
    public List<Edge> getEdges(int nodeId) {
        return adjacencyList.get(nodeId);
    }

    /**
     * Creates an EventNode and adds it into the {@code nodes} list
     * @param node
     * @return nodeId
     */
    private int addNode(
        int tripId,
        int stopId,
        int time,
        EventType type
    ) {
        // nodeId will simply increment according to the size of the node list
        int nodeId = nodes.size();

        EventNode node = new EventNode();
        node.tripId = tripId;
        node.stopId = stopId;
        node.time = time;
        node.type = type;

        nodes.add(node);
        adjacencyList.add(new ArrayList<>()); // Each EventNode has it's own list of outgoing edges

        return nodeId;
    }

    /**
     * Adds an Edge into the edge list of a specified EventNode, stored in {@code adjacencyList}
     * @param fromNodeId
     * @param toNodeId
     * @param weight
     */
    private void addEdge(int fromNodeId, int toNodeId, int weight) {
        if (weight < 0) throw new IllegalArgumentException("Edge weight cannot be negative!");

        adjacencyList.get(fromNodeId).add(new Edge(toNodeId, weight));
    }

    public long structureBytes() {
        long bytes = 0;

        // Approximate EventNode storage
        bytes += (long) nodes.size() * 16;

        // Approximate adjacency list references
        bytes += (long) adjacencyList.size() * 8;

        // Approximate Edge storage
        bytes += (long) numArcs() * 8;

        return bytes;
    }



    /**
     * @param connections   all connections, in any order
     * @param numStops      number of stops; stop ids are 0..numStops-1
     * @param transferTimeBuffer minimum transfer time in minutes
     */
    public TimeExpandedGraph(
        Connection[] connections,
        int numStops,
        int transferTimeBuffer
    ) {
        // Validation
        if (connections == null) throw new IllegalArgumentException("Connections cannot be null!");
        if (numStops < 0) throw new IllegalArgumentException("Number of stops cannot be negative!");
        if (transferTimeBuffer < 0) throw new IllegalArgumentException("Transfer time cannot be negative!");

        this.connections = connections.clone();
        this.numStops = numStops;
        this.transferTimeMin = transferTimeBuffer;

        this.nodes = new ArrayList<>();
        this.adjacencyList = new ArrayList<>();

        buildGraph();
    }



    private void buildGraph() {

        int[] departureNodes = new int[connections.length];
        int[] arrivalNodes = new int[connections.length];

        // Create EventNodes (one departure, one arrival for each Connection) and travel Edges
        for (int i = 0; i < connections.length; i++) {
            Connection c = connections[i];

            departureNodes[i] = addNode(
                c.tripId(), c.from(), c.depTime(), EventType.DEPARTURE
            );

            arrivalNodes[i] = addNode(
                c.tripId(), c.to(), c.arrTime(), EventType.ARRIVAL
            );

            addEdge(
                departureNodes[i],
                arrivalNodes[i],
                c.arrTime() - c.depTime()
            );
        }

        // Create dwell and transfer Edges
        for (int i = 0; i < connections.length; i++) {
            Connection current = connections[i];

            for (int j = 0; j < connections.length; j++) {
                if (i == j) continue;

                Connection next = connections[j];

                if (current.to() != next.from()) continue;

                if (current.tripId() == next.tripId()) {
                    // Dwell edge
                    if (next.depTime() >= current.arrTime()) {
                        addEdge(
                            arrivalNodes[i],
                            departureNodes[j],
                            next.depTime() - current.arrTime()
                        );
                    }
                } else {
                    // Transfer edge
                    if (next.depTime() >= current.arrTime() + transferTimeMin) {
                        addEdge(
                            arrivalNodes[i],
                            departureNodes[j],
                            next.depTime() - current.arrTime()
                        );
                    }
                }
            }
        }
    }
}
