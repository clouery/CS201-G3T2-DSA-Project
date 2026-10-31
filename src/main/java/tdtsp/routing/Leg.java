package tdtsp.routing;

/**
 * One routed leg between consecutive tour cities (plan §1.3, §2).
 *
 * <p>{@code arrival - depart = waiting + ride}. Waiting includes the wait at the
 * origin and every transfer buffer.
 *
 * @param from      origin stop id
 * @param to        destination stop id
 * @param depart    time the traveller is ready at {@code from}
 * @param arrival   arrival time at {@code to}, or {@link Router#INF}
 * @param ride      total in-vehicle minutes
 * @param waiting   total waiting minutes
 * @param transfers vehicle changes (boardings minus one)
 */
public record Leg(int from, int to, int depart, int arrival, int ride, int waiting, int transfers) {

    public static Leg infeasible(int from, int to, int depart) {
        return new Leg(from, to, depart, Router.INF, 0, 0, 0);
    }

    /** A leg built from its arrival, ride time and number of boardings. */
    public static Leg of(int from, int to, int depart, int arrival, int ride, int boardings) {
        return new Leg(from, to, depart, arrival, ride, arrival - depart - ride, Math.max(0, boardings - 1));
    }

    public boolean feasible() {
        return arrival != Router.INF;
    }
}
