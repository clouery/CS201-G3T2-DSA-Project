package tdtsp.routing;

/**
 * Each {@code Connection} represents one scheduled journey between two stops (plan §5.4).
 * @param depTime Departure time in minutes since 00:00
 * @param from ID of the departing stop
 * @param arrTime Arrival time in minutes since 00:00
 * @param to ID of the arriving stop
 * @param to ID of the trip/specific train service
 */
public record Connection(int depTime, int from, int arrTime, int to, int tripId) {
}
