package tdtsp.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Approach A: Time-Expanded Event Graph + Dijkstra (plan §5.3).
 *
 * Every connection {@code i} gets three event nodes:
 * <ul>
 *   <li>{@code dep(i)}: the vehicle leaves {@code from};</li>
 *   <li>{@code arr(i)}: the vehicle reaches {@code to};</li>
 *   <li>{@code tr(i)}: a traveller waiting at {@code from}, ready to board {@code i}.</li>
 * </ul>
 * Arcs (weight = elapsed minutes):
 * <ul>
 *   <li>travel {@code dep(i) -> arr(i)};</li>
 *   <li>stay on board {@code arr(i) -> dep(j)}, {@code j} the trip's next connection;</li>
 *   <li>transfer {@code arr(i) -> tr(k)}, the first {@code tr} node at {@code to} with time
 *       {@code >= arrival + buffer};</li>
 *   <li>waiting {@code tr(k) -> tr(k')} between consecutive departures at the same stop;</li>
 *   <li>boarding {@code tr(i) -> dep(i)}.</li>
 * </ul>
 * The graph is a static DAG, so plain Dijkstra on it gives the earliest arrival.
 *
 * Time: O((V' + E') log V') per query. Space: O(V' + E') with V' = 3C, E' &lt;= 5C.
 */
public class TimeExpandedGraph implements Router {

    private final Connection[] connections;
    private final int numConnections;
    private final int numStops;
    /** Per stop: connection indices departing there, in departure order, and their times. */
    private final int[][] departuresByStop;
    private final int[][] departureTimesByStop;
    /** Compressed Sparse Row (CSR) data structure that stores the graph's adjacency list using flat arrays */
    private final int[] offsets;
    private final int[] targets;
    private final int[] weights;

    /**
     * @param connections all connections, in any order (a sorted copy is kept)
     * @param numStops    number of stops; stop ids are {@code 0..numStops-1}
     * @param buffer      transfer buffer in minutes, not applied at the origin
     */
    public TimeExpandedGraph(Connection[] connections, int numStops, int buffer) {
        this.connections = connections.clone();
        Arrays.sort(this.connections, Comparator.comparingInt(Connection::depTime));
        this.numConnections = this.connections.length;
        this.numStops = numStops;

        List<List<Integer>> byStop = new ArrayList<>();
        for (int s = 0; s < numStops; s++) {
            byStop.add(new ArrayList<>());
        }
        for (int i = 0; i < this.numConnections; i++) {
            byStop.get(this.connections[i].from()).add(i);
        }
        this.departuresByStop = new int[numStops][];
        this.departureTimesByStop = new int[numStops][];
        for (int s = 0; s < numStops; s++) {
            this.departuresByStop[s] = byStop.get(s).stream().mapToInt(Integer::intValue).toArray();
            this.departureTimesByStop[s] = Arrays.stream(this.departuresByStop[s]).map(i -> this.connections[i].depTime()).toArray();
        }

        int maxArcs = 5 * this.numConnections;
        int[] from = new int[maxArcs];
        int[] to = new int[maxArcs];
        int[] w = new int[maxArcs];
        int m = 0;
        int numTrips = Arrays.stream(this.connections).mapToInt(Connection::tripId).max().orElse(-1) + 1;
        int[] lastOfTrip = new int[numTrips];
        Arrays.fill(lastOfTrip, -1);

        for (int i = 0; i < numConnections; i++) {
            Connection ci = this.connections[i];
            // travel
            from[m] = dep(i);
            to[m] = arr(i);
            w[m++] = ci.arrTime() - ci.depTime();
            // stay on board from the trip's previous connection
            int p = lastOfTrip[ci.tripId()];
            if (p >= 0 && this.connections[p].to() == ci.from()) {
                from[m] = arr(p);
                to[m] = dep(i);
                w[m++] = ci.depTime() - this.connections[p].arrTime();
            }
            lastOfTrip[ci.tripId()] = i;
            // transfer at the arrival stop
            int stop = ci.to();
            int k = Timetable.nextDepartureIndex(departureTimesByStop[stop], ci.arrTime() + buffer);
            if (k != Timetable.NONE) {
                from[m] = arr(i);
                to[m] = tr(departuresByStop[stop][k]);
                w[m++] = departureTimesByStop[stop][k] - ci.arrTime();
            }
            // board
            from[m] = tr(i);
            to[m] = dep(i);
            w[m++] = 0;
        }
        for (int s = 0; s < numStops; s++) {
            for (int k = 0; k + 1 < departuresByStop[s].length; k++) {
                from[m] = tr(departuresByStop[s][k]);
                to[m] = tr(departuresByStop[s][k + 1]);
                w[m++] = departureTimesByStop[s][k + 1] - departureTimesByStop[s][k];
            }
        }

        int nodes = 3 * numConnections;
        offsets = new int[nodes + 1];
        for (int a = 0; a < m; a++) {
            offsets[from[a] + 1]++;
        }
        for (int v = 0; v < nodes; v++) {
            offsets[v + 1] += offsets[v];
        }
        targets = new int[m];
        weights = new int[m];
        int[] fill = Arrays.copyOf(offsets, nodes);
        for (int a = 0; a < m; a++) {
            int slot = fill[from[a]]++;
            targets[slot] = to[a];
            weights[slot] = w[a];
        }
    }

    private int dep(int i) {
        return i;
    }

    private int arr(int i) {
        return numConnections + i;
    }

    private int tr(int i) {
        return 2 * numConnections + i;
    }

    /** Earliest arrival at every stop when leaving {@code s} at {@code t0}. */
    public int[] earliestArrival(int s, int t0) {
        return search(s, t0, -1).bestTime();
    }

    @Override
    public int earliestArrival(int from, int to, int time) {
        if (time == INF) {
            return INF;
        }
        return search(from, time, to).bestTime()[to];
    }

    @Override
    public Leg leg(int from, int to, int time) {
        if (time == INF) {
            return Leg.infeasible(from, to, time);
        }
        Search r = search(from, time, to);
        if (r.bestTime()[to] == INF) {
            return Leg.infeasible(from, to, time);
        }
        if (from == to) {
            return Leg.of(from, to, time, time, 0, 0);
        }
        int ride = 0;
        int boardings = 1;
        for (int v = r.bestNode()[to]; r.parent()[v] >= 0; v = r.parent()[v]) {
            int p = r.parent()[v];
            boolean parentIsArr = p >= numConnections && p < 2 * numConnections;

            // Vehicle ride: departure node -> arrival node
            if (p < numConnections && v >= numConnections && v < 2 * numConnections) {
                ride += r.dist()[v] - r.dist()[p];
            }

            // Transfer: arrival node -> traveller-ready node
            else if (parentIsArr && v >= 2 * numConnections) {
                boardings++;
            }
        }
        return Leg.of(from, to, time, r.bestTime()[to], ride, boardings);
    }

    /** Dijkstra from the first departure at {@code s} at or after {@code t0}. */
    private Search search(int s, int t0, int target) {
        int nodes = 3 * numConnections;
        int[] dist = new int[nodes];
        Arrays.fill(dist, INF);
        int[] parent = new int[nodes];
        Arrays.fill(parent, -1);
        int[] bestTime = new int[numStops];
        Arrays.fill(bestTime, INF);
        int[] bestNode = new int[numStops];
        Arrays.fill(bestNode, -1);
        bestTime[s] = t0;
        Search result = new Search(dist, parent, bestTime, bestNode);

        int k = Timetable.nextDepartureIndex(departureTimesByStop[s], t0);
        if (target == s || k == Timetable.NONE) {
            return result;
        }
        int source = tr(departuresByStop[s][k]);
        dist[source] = departureTimesByStop[s][k];

        // Entries pack (time << 32 | node), so the natural long order is by time.
        PriorityQueue<Long> pq = new PriorityQueue<>();
        pq.add(((long) dist[source] << 32) | source);
        while (!pq.isEmpty()) {
            long top = pq.poll();
            int time = (int) (top >>> 32);
            int u = (int) top;
            if (time > dist[u]) {
                continue;
            }
            if (u >= numConnections && u < 2 * numConnections) {
                int stop = connections[u - numConnections].to();
                if (bestNode[stop] < 0 && stop != s) {
                    bestTime[stop] = time;
                    bestNode[stop] = u;
                    if (stop == target) {
                        break;
                    }
                }
            }
            for (int a = offsets[u]; a < offsets[u + 1]; a++) {
                int v = targets[a];
                int nd = time + weights[a];
                if (nd < dist[v]) {
                    dist[v] = nd;
                    parent[v] = u;
                    pq.add(((long) nd << 32) | v);
                }
            }
        }
        return result;
    }

    public int numNodes() {
        return 3 * numConnections;
    }

    public int numArcs() {
        return targets.length;
    }

    /** Estimated bytes of the preprocessed structure (CSR arrays, per-stop departures, connections). */
    public long structureBytes() {
        return 4L * (offsets.length + targets.length + weights.length)
                + 8L * numConnections + 16L * 2 * numStops
                + 40L * numConnections;
    }

    private record Search(int[] dist, int[] parent, int[] bestTime, int[] bestNode) {
    }
}
