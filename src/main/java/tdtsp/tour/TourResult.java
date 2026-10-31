package tdtsp.tour;

import java.util.List;

import tdtsp.routing.Leg;

/**
 * A tour simulated on the timetable (plan §5.6).
 *
 * @param tour           city order, starting with the start city
 * @param legs           routed legs, one per consecutive pair
 * @param start          departure time from the start city
 * @param arrival        arrival at the last city (infeasible legs are skipped, see {@link TourEvaluator})
 * @param waiting        total waiting minutes over feasible legs
 * @param ride           total riding minutes over feasible legs
 * @param transfers      total transfers over feasible legs
 * @param infeasibleLegs legs with no connection
 */
public record TourResult(List<Integer> tour, List<Leg> legs, int start, int arrival,
                         int waiting, int ride, int transfers, int infeasibleLegs) {

    /** Penalty per infeasible leg, in minutes; dominates any feasible elapsed time. */
    public static final long PENALTY = 1_000_000L;

    public int elapsed() {
        return arrival - start;
    }

    /** Objective minimised by local search: penalised elapsed time. */
    public long cost() {
        return infeasibleLegs * PENALTY + elapsed();
    }

    public boolean feasible() {
        return infeasibleLegs == 0;
    }
}
