package tdtsp.routing;

/**
 * One vehicle hop between consecutive stops of a trip (plan §5.4).
 */
public record Connection(int depTime, int from, int arrTime, int to, int tripId) {
}
