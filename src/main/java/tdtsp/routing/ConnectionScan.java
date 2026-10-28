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
 * a single O(C) scan with O(C + V + T) space and no priority queue.
 */
public class ConnectionScan implements Router {

    private final Connection[] connections;
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
        this.numStops = numStops;
        this.numTrips = Arrays.stream(connections).mapToInt(Connection::tripId).max().orElse(-1) + 1;
        this.buffer = buffer;
    }

    /** Earliest arrival at every stop when leaving {@code s} at {@code t0}. */
    public int[] earliestArrival(int s, int t0) {
        return scan(s, t0, -1);
    }

    @Override
    public int earliestArrival(int from, int to, int time) {
        if (time == INF) {
            return INF;
        }
        return scan(from, time, to)[to];
    }

    /** Scans connections in departure order; stops early once {@code target} is settled. */
    private int[] scan(int s, int t0, int target) {
        int[] arr = new int[numStops];
        Arrays.fill(arr, INF);
        arr[s] = t0;
        boolean[] boarded = new boolean[numTrips];

        for (Connection c : connections) {
            if (target >= 0 && c.depTime() >= arr[target]) {
                break;
            }
            boolean canBoard = arr[c.from()] != INF
                    && c.depTime() >= arr[c.from()] + (c.from() == s ? 0 : buffer);
            if (boarded[c.tripId()] || canBoard) {
                boarded[c.tripId()] = true;
                if (c.arrTime() < arr[c.to()]) {
                    arr[c.to()] = c.arrTime();
                }
            }
        }
        return arr;
    }
}
