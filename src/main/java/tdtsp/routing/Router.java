package tdtsp.routing;

/**
 * Earliest-arrival router used by the tour layer (plan §5.6).
 * Times are integer minutes from the service-day start.
 */
public interface Router {

    /** Marks an unreachable stop or an infeasible leg. */
    int INF = Integer.MAX_VALUE;

    /** Earliest arrival at {@code to} when leaving {@code from} at {@code time}, or {@link #INF}. */
    default int earliestArrival(int from, int to, int time) {
        return leg(from, to, time).arrival();
    }

    /** The routed journey from {@code from} to {@code to}, with its waiting/riding breakdown. */
    Leg leg(int from, int to, int time);
}
