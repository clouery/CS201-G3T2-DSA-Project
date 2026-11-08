package tdtsp.routing;

/**
 * One vehicle hop between consecutive stops of a trip.
 */
public record Connection(int depTime, int from, int arrTime, int to, int tripId) {
}
