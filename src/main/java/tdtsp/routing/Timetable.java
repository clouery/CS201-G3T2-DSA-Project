package tdtsp.routing;

/** Timetable helpers shared by the routers (plan §5.1). */
public final class Timetable {

    /** Returned by {@link #nextDeparture} when no departure is late enough. */
    public static final int NONE = -1;

    private Timetable() {
    }

    /**
     * Binary search for the first departure {@code >= t} in an ascending array.
     * O(log k) for k departures.
     */
    public static int nextDeparture(int[] departures, int t) {
        int i = nextDepartureIndex(departures, t);
        return i == NONE ? NONE : departures[i];
    }

    /** Index of the first departure {@code >= t}, or {@link #NONE}. */
    public static int nextDepartureIndex(int[] departures, int t) {
        int lo = 0;
        int hi = departures.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (departures[mid] < t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo < departures.length ? lo : NONE;
    }
}
