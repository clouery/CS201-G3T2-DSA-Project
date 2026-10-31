package tdtsp.routing;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Time-dependent Dijkstra (plan §5.5, §5.7).
 *
 * <p>Unlike the plan's pseudocode, labels are (stop, trip) states rather than stops:
 * staying on the same trip through a stop costs no transfer buffer, while changing
 * trips does. A per-stop label cannot tell these apart and would force a transfer at
 * every intermediate stop.
 *
 * <p>With {@link Objective#ARRIVAL} the result is the exact earliest arrival. With
 * {@link Objective#WAIT} or {@link Objective#RIDE} labels are ordered by waiting or
 * riding time instead; those are the plan's secondary leg heuristics, not exact
 * optimisers of their own cost.
 *
 * <p>Time: O((E + C) log C). Space: O(V + E + C).
 */
public class TimeDependentDijkstra implements Router {

    /** What the search minimises (plan §2). */
    public enum Objective { ARRIVAL, WAIT, RIDE }

    private static final int NO_TRIP = -1;

    private static final Comparator<Label> ORDER =
            Comparator.comparingInt((Label l) -> l.cost).thenComparingInt(l -> l.time);

    private final List<List<Edge>> adj;
    private final int buffer;
    private final Objective objective;

    /**
     * @param adj    adjacency list indexed by stop id
     * @param buffer transfer buffer in minutes, not applied at the origin
     */
    public TimeDependentDijkstra(List<List<Edge>> adj, int buffer) {
        this(adj, buffer, Objective.ARRIVAL);
    }

    public TimeDependentDijkstra(List<List<Edge>> adj, int buffer, Objective objective) {
        this.adj = adj;
        this.buffer = buffer;
        this.objective = objective;
    }

    /** Arrival at every stop when leaving {@code s} at {@code t0}. */
    public int[] earliestArrival(int s, int t0) {
        Label[] best = search(s, t0, -1);
        int[] arr = new int[best.length];
        for (int v = 0; v < best.length; v++) {
            arr[v] = best[v] == null ? INF : best[v].time;
        }
        return arr;
    }

    @Override
    public int earliestArrival(int from, int to, int time) {
        if (time == INF) {
            return INF;
        }
        Label l = search(from, time, to)[to];
        return l == null ? INF : l.time;
    }

    @Override
    public Leg leg(int from, int to, int time) {
        if (time == INF) {
            return Leg.infeasible(from, to, time);
        }
        Label l = search(from, time, to)[to];
        if (l == null) {
            return Leg.infeasible(from, to, time);
        }
        int boardings = 0;
        int previousTrip = NO_TRIP;
        for (Label x = l; x.parent != null; x = x.parent) {
            if (x.trip != previousTrip) {
                boardings++;
                previousTrip = x.trip;
            }
        }
        return Leg.of(from, to, time, l.time, l.ride, boardings);
    }

    /** Settled label per stop; stops early once {@code target} is settled. */
    private Label[] search(int s, int t0, int target) {
        Label[] best = new Label[adj.size()];
        // Transfers out of a stop only use its first settled label.
        boolean[] transferred = new boolean[adj.size()];
        Set<Long> riding = new HashSet<>();

        PriorityQueue<Label> pq = new PriorityQueue<>(ORDER);
        pq.add(new Label(t0, 0, 0, s, NO_TRIP, null));

        while (!pq.isEmpty()) {
            Label l = pq.poll();
            if (l.trip != NO_TRIP && !riding.add(((long) l.trip << 32) | l.stop)) {
                continue;
            }
            if (best[l.stop] == null) {
                best[l.stop] = l;
                if (l.stop == target) {
                    break;
                }
            }

            if (l.trip != NO_TRIP) {
                // Stay on board: same trip, no buffer.
                for (Edge e : adj.get(l.stop)) {
                    int i = indexOfTrip(e, l.trip, l.time);
                    if (i != Timetable.NONE) {
                        pq.add(extend(l, e, i));
                    }
                }
            }

            if (!transferred[l.stop]) {
                transferred[l.stop] = true;
                int tReady = l.time + (l.trip == NO_TRIP ? 0 : buffer);
                for (Edge e : adj.get(l.stop)) {
                    int i = Timetable.nextDepartureIndex(e.departures(), tReady);
                    if (i != Timetable.NONE) {
                        pq.add(extend(l, e, i));
                    }
                }
            }
        }
        return best;
    }

    private Label extend(Label l, Edge e, int i) {
        int dep = e.departures()[i];
        int arr = dep + e.rideTime();
        int ride = l.ride + e.rideTime();
        int cost = switch (objective) {
            case ARRIVAL -> arr;
            case WAIT -> l.cost + (dep - l.time);
            case RIDE -> ride;
        };
        return new Label(arr, cost, ride, e.to(), e.tripIds()[i], l);
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

    /** Estimated bytes of the preprocessed structure (edges and departure arrays). */
    public long structureBytes() {
        long bytes = 0;
        for (List<Edge> edges : adj) {
            bytes += 16 + 4L * edges.size();
            for (Edge e : edges) {
                bytes += 32 + 2 * (16 + 4L * e.departures().length);
            }
        }
        return bytes;
    }

    /** Search state: at {@code stop} at {@code time}, having arrived on {@code trip}. */
    private record Label(int time, int cost, int ride, int stop, int trip, Label parent) {
    }
}
