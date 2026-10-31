package tdtsp.routing;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Connection Scan Algorithm for earliest arrival (plan §5.4, §5.7).
 *
 * <p>Unlike the plan's pseudocode, it also records which trips have been boarded
 * (Dibbelt et al., 2018): a connection on a boarded trip is taken without the
 * transfer buffer, so riding through a stop is not treated as a transfer.
 *
 * <p>Connections are sorted once in the constructor, O(C log C); each query is
 * a single O(C) scan with O(C + V + T) space and no priority queue. The scan
 * starts at the first connection departing at or after the query time.
 */
public class ConnectionScan implements Router {

    private final Connection[] connections;
    private final int[] depTimes;
    private final int numStops;
    private final int numTrips;
    private final int buffer;

    /**
     * @param connections all connections, in any order (a sorted copy is kept)
     * @param numStops    number of stops; stop ids are {@code 0..numStops-1}
     * @param buffer      transfer buffer in minutes, not applied at the origin
     */
    public ConnectionScan(Connection[] connections, int numStops, int buffer) {
        this.connections = connections.clone();
        Arrays.sort(this.connections, Comparator.comparingInt(Connection::depTime));
        this.depTimes = Arrays.stream(this.connections).mapToInt(Connection::depTime).toArray();
        this.numStops = numStops;
        this.numTrips = Arrays.stream(connections).mapToInt(Connection::tripId).max().orElse(-1) + 1;
        this.buffer = buffer;
    }

    /** Earliest arrival at every stop when leaving {@code s} at {@code t0}. */
    public int[] earliestArrival(int s, int t0) {
        return scan(s, t0, -1).arr();
    }

    @Override
    public int earliestArrival(int from, int to, int time) {
        if (time == INF) {
            return INF;
        }
        return scan(from, time, to).arr()[to];
    }

    @Override
    public Leg leg(int from, int to, int time) {
        if (time == INF) {
            return Leg.infeasible(from, to, time);
        }
        Scan scan = scan(from, time, to);
        if (scan.arr()[to] == INF) {
            return Leg.infeasible(from, to, time);
        }
        // Walk the journey pointers back: each pointer is one trip segment.
        int ride = 0;
        int boardings = 0;
        for (int v = to; v != from; ) {
            Connection enter = connections[scan.inEnter()[v]];
            Connection exit = connections[scan.inExit()[v]];
            ride += exit.arrTime() - enter.depTime();
            boardings++;
            v = enter.from();
        }
        return Leg.of(from, to, time, scan.arr()[to], ride, boardings);
    }

    /** Scans connections in departure order; stops early once {@code target} is settled. */
    private Scan scan(int s, int t0, int target) {
        int[] arr = new int[numStops];
        Arrays.fill(arr, INF);
        arr[s] = t0;
        int[] inEnter = new int[numStops];
        int[] inExit = new int[numStops];
        int[] enter = new int[numTrips];
        Arrays.fill(enter, -1);

        int start = Timetable.nextDepartureIndex(depTimes, t0);
        if (start == Timetable.NONE || target == s) {
            return new Scan(arr, inEnter, inExit);
        }
        for (int i = start; i < connections.length; i++) {
            Connection c = connections[i];
            if (target >= 0 && c.depTime() >= arr[target]) {
                break;
            }
            boolean canBoard = arr[c.from()] != INF
                    && c.depTime() >= arr[c.from()] + (c.from() == s ? 0 : buffer);
            if (enter[c.tripId()] >= 0 || canBoard) {
                if (enter[c.tripId()] < 0) {
                    enter[c.tripId()] = i;
                }
                if (c.arrTime() < arr[c.to()]) {
                    arr[c.to()] = c.arrTime();
                    inEnter[c.to()] = enter[c.tripId()];
                    inExit[c.to()] = i;
                }
            }
        }
        return new Scan(arr, inEnter, inExit);
    }

    /** Estimated bytes of the preprocessed structure (sorted connections and their times). */
    public long structureBytes() {
        return 40L * connections.length + 16 + 4L * depTimes.length;
    }

    public int size() {
        return connections.length;
    }

    /** Arrival labels plus the journey pointers (boarding and alighting connection) per stop. */
    private record Scan(int[] arr, int[] inEnter, int[] inExit) {
    }
}
