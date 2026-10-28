package tdtsp.routing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Time-dependent Dijkstra for earliest arrival (plan §5.7).
 *
 * <p>Unlike the plan's pseudocode, labels are (stop, trip) states rather than stops:
 * staying on the same trip through a stop costs no transfer buffer, while changing
 * trips does. A per-stop label cannot tell these apart and would force a transfer at
 * every intermediate stop.
 *
 * <p>Time: O((E + C) log C). Space: O(V + E + C).
 */
public class TimeDependentDijkstra implements Router {

    private static final int NO_TRIP = -1;

    private final List<List<Edge>> adj;
    private final int buffer;

    /**
     * @param adj    adjacency list indexed by stop id
     * @param buffer transfer buffer in minutes, not applied at the origin
     */
    public TimeDependentDijkstra(List<List<Edge>> adj, int buffer) {
        this.adj = adj;
        this.buffer = buffer;
    }

    /** Earliest arrival at every stop when leaving {@code s} at {@code t0}. */
    public int[] earliestArrival(int s, int t0) {
        int n = adj.size();
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[s] = t0;

        // Transfers out of a stop only need its earliest arrival; later ones are dominated.
        boolean[] transferred = new boolean[n];
        Set<Long> riding = new HashSet<>();

        // Entries are {time, stop, trip arrived on}; NO_TRIP marks the origin.
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        pq.add(new int[] {t0, s, NO_TRIP});

        while (!pq.isEmpty()) {
            int[] top = pq.poll();
            int time = top[0];
            int u = top[1];
            int trip = top[2];

            if (trip != NO_TRIP) {
                if (!riding.add(((long) trip << 32) | u)) {
                    continue;
                }
                // Stay on board: same trip, no buffer.
                for (Edge e : adj.get(u)) {
                    int i = indexOfTrip(e, trip, time);
                    if (i != Timetable.NONE) {
                        relax(pq, dist, e, i);
                    }
                }
            }

            if (!transferred[u]) {
                transferred[u] = true;
                int tReady = time + (trip == NO_TRIP ? 0 : buffer);
                for (Edge e : adj.get(u)) {
                    int i = Timetable.nextDepartureIndex(e.departures(), tReady);
                    if (i != Timetable.NONE) {
                        relax(pq, dist, e, i);
                    }
                }
            }
        }
        return dist;
    }

    @Override
    public int earliestArrival(int from, int to, int time) {
        if (time == INF) {
            return INF;
        }
        return earliestArrival(from, time)[to];
    }

    private static void relax(PriorityQueue<int[]> pq, int[] dist, Edge e, int i) {
        int arr = e.departures()[i] + e.rideTime();
        if (arr < dist[e.to()]) {
            dist[e.to()] = arr;
        }
        pq.add(new int[] {arr, e.to(), e.tripIds()[i]});
    }

    /** Index of {@code trip}'s departure on {@code e} at or after {@code time}, or NONE. */
    private static int indexOfTrip(Edge e, int trip, int time) {
        int start = Timetable.nextDepartureIndex(e.departures(), time);
        if (start == Timetable.NONE) {
            return Timetable.NONE;
        }
        for (int i = start; i < e.tripIds().length; i++) {
            if (e.tripIds()[i] == trip) {
                return i;
            }
        }
        return Timetable.NONE;
    }
}
