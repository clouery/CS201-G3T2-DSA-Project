package tdtsp.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

public class TimeExpandedDijkstra implements Router {

    private final TimeExpandedGraph graph;
    
    // We use a priority queue based on the distance
    private record QueueEntry(int nodeId, int distance) {}

    public record RouteResult(
        int arrivalTime,
        List<Integer> nodeIds
    ) {}

    public TimeExpandedGraph getGraph() {
        return graph;
    }



    public TimeExpandedDijkstra(TimeExpandedGraph graph) {
        this.graph = graph;
    }


    public int[] earliestArrival(int originStopId, int departureTime) {
        int numNodes = graph.numNodes();

        int[] dist = new int[numNodes];
        Arrays.fill(dist, Router.INF);

        PriorityQueue<QueueEntry> pq = new PriorityQueue<>(
            (a, b) -> Integer.compare(a.distance(), b.distance())
        );

        // Initialise all eligible departure events at the origin.
        // No transfer buffer is needed for the initial boarding.
        for (int i = 0; i < numNodes; i++) {
            TimeExpandedGraph.EventNode node = graph.getNode(i);

            if (node.type == TimeExpandedGraph.EventType.DEPARTURE
                && node.stopId == originStopId
                && node.time >= departureTime) {

                int waitingTime = node.time - departureTime;

                if (waitingTime < dist[i]) {
                    dist[i] = waitingTime;
                    pq.add(new QueueEntry(i, waitingTime));
                }
            }
        }

        // Run Dijkstra until every reachable event has been processed.
        while (!pq.isEmpty()) {
            QueueEntry current = pq.poll();

            int currentNodeId = current.nodeId();
            int currentDistance = current.distance();

            if (currentDistance != dist[currentNodeId]) {
                continue;
            }

            for (TimeExpandedGraph.Edge edge : graph.getEdges(currentNodeId)) {
                int nextNodeId = edge.to();
                int newDistance = currentDistance + edge.weight();

                if (newDistance < dist[nextNodeId]) {
                    dist[nextNodeId] = newDistance;
                    pq.add(new QueueEntry(nextNodeId, newDistance));
                }
            }
        }

        // Convert event-node distances into earliest arrival times per stop.
        int[] arrivalTimes = new int[graph.numStops()];
        Arrays.fill(arrivalTimes, Router.INF);

        // Travelling from a stop to itself takes no time.
        arrivalTimes[originStopId] = departureTime;

        for (int i = 0; i < numNodes; i++) {
            TimeExpandedGraph.EventNode node = graph.getNode(i);

            if (node.type == TimeExpandedGraph.EventType.ARRIVAL
                && dist[i] != Router.INF) {

                int arrivalTime = departureTime + dist[i];

                arrivalTimes[node.stopId] = Math.min(
                    arrivalTimes[node.stopId],
                    arrivalTime
                );
            }
        }

        return arrivalTimes;
    }

    public Optional<RouteResult> findEarliestArrival(
        int originStopId,
        int destinationStopId,
        int departureTime
    ) {
        int numNodes = graph.numNodes();

        // Distance from the requested departure time to each node
        int[] dist = new int[numNodes];
        Arrays.fill(dist, Integer.MAX_VALUE);

        // Stores the previous node in the shortest path
        int[] prev = new int[numNodes];
        Arrays.fill(prev, -1);

        // Priority Queue: smallest distance is processed first
        PriorityQueue<QueueEntry> pq = new PriorityQueue<>(
            (a, b) -> Integer.compare(a.distance(), b.distance())
        );

        // Handle the case where origin and destination are identical
        if (originStopId == destinationStopId) {
            return Optional.of(
                new RouteResult(departureTime, List.of())
            );
        }

        // Initialise all possible first departures
        for (int i = 0; i < numNodes; i++) {
            TimeExpandedGraph.EventNode node = graph.getNode(i);

            if (node.type == TimeExpandedGraph.EventType.DEPARTURE &&
                node.stopId == originStopId &&
                node.time >= departureTime) {

                int waitingTime = node.time - departureTime;

                if (waitingTime < dist[i]) {
                    dist[i] = waitingTime;
                    pq.add(new QueueEntry(i, waitingTime));
                }
            }
        }

        // Dijkstra's algorithm
        while (!pq.isEmpty()) {

            QueueEntry current = pq.poll();

            int currentNodeId = current.nodeId();
            int currentDistance = current.distance();

            // Ignore outdated Priority Queue entries
            if (currentDistance != dist[currentNodeId]) {
                continue;
            }

            TimeExpandedGraph.EventNode currentNode =
                graph.getNode(currentNodeId);

            // Check whether we have reached the destination
            if (currentNode.type == TimeExpandedGraph.EventType.ARRIVAL &&
                currentNode.stopId == destinationStopId) {

                List<Integer> path = reconstructPath(prev, currentNodeId);

                return Optional.of(
                    new RouteResult(
                        departureTime + currentDistance,
                        path
                    )
                );
            }

            // Explore outgoing edges
            for (TimeExpandedGraph.Edge edge : graph.getEdges(currentNodeId)) {

                int nextNodeId = edge.to();
                int newDistance = currentDistance + edge.weight();

                if (newDistance < dist[nextNodeId]) {

                    dist[nextNodeId] = newDistance;
                    prev[nextNodeId] = currentNodeId;

                    pq.add(new QueueEntry(nextNodeId, newDistance));
                }
            }
        }

        // No valid journey was found
        return Optional.empty();
    }

    @Override
    public Leg leg(int from, int to, int time) {

        Optional<RouteResult> result = findEarliestArrival(
            from,
            to,
            time
        );

        // No valid route exists
        if (result.isEmpty()) {
            return Leg.infeasible(from, to, time);
        }

        RouteResult route = result.get();

        int rideTime = 0;
        int boardings = 0;

        List<Integer> path = route.nodeIds();

        // Count the initial boarding
        if (!path.isEmpty()) {
            boardings = 1;
        }

        // Calculate riding time and additional boardings
        for (int i = 1; i < path.size(); i++) {

            TimeExpandedGraph.EventNode previous =
                graph.getNode(path.get(i - 1));

            TimeExpandedGraph.EventNode current =
                graph.getNode(path.get(i));

            // Travel edge: departure -> arrival
            if (previous.type == TimeExpandedGraph.EventType.DEPARTURE &&
                current.type == TimeExpandedGraph.EventType.ARRIVAL) {

                rideTime += current.time - previous.time;
            }

            // Dwell or transfer edge: arrival -> departure
            else if (previous.type == TimeExpandedGraph.EventType.ARRIVAL &&
                    current.type == TimeExpandedGraph.EventType.DEPARTURE) {

                // A different trip means the passenger boarded another vehicle
                if (previous.tripId != current.tripId) {
                    boardings++;
                }
            }
        }

        return Leg.of(
            from,
            to,
            time,
            route.arrivalTime(),
            rideTime,
            boardings
        );
    }

    private List<Integer> reconstructPath(int[] prev, int destinationNodeId) {

        List<Integer> path = new ArrayList<>();

        int current = destinationNodeId;

        while (current != -1) {
            path.add(current);
            current = prev[current];
        }

        // The path was collected backwards, so reverse it
        Collections.reverse(path);

        return path;
    }
}