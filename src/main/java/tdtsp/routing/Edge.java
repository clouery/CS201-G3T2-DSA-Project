package tdtsp.routing;

/**
 * Directed time-dependent edge {@code u -> to} for one line and direction (plan §5.1).
 *
 * <p>Build one edge per line segment, so all trips on it share the ride time and
 * an earlier departure always arrives earlier (FIFO).
 *
 * @param to         head stop id
 * @param departures departure times from the tail, sorted ascending
 * @param tripIds    trip of each departure, parallel to {@code departures}
 * @param rideTime   in-vehicle minutes from tail to head
 */
public record Edge(int to, int[] departures, int[] tripIds, int rideTime) {

    public Edge {
        if (departures.length != tripIds.length) {
            throw new IllegalArgumentException("departures and tripIds must have the same length");
        }
    }
}
