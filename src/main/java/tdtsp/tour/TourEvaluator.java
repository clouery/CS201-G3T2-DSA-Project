package tdtsp.tour;

import java.util.ArrayList;
import java.util.List;

import tdtsp.routing.Leg;
import tdtsp.routing.Router;

/**
 * Simulates a tour leg by leg on the timetable (plan §5.6).
 *
 * <p>An infeasible leg is counted and penalised, and the traveller continues from
 * the next city at the same time, so one bad leg does not hide the rest of the tour.
 */
public final class TourEvaluator {

    private TourEvaluator() {
    }

    public static TourResult evaluate(List<Integer> tour, int t0, Router router) {
        return evaluate(tour, t0, router, Long.MAX_VALUE);
    }

    /**
     * As {@link #evaluate(List, int, Router)}, but returns {@code null} as soon as the
     * partial cost reaches {@code bound}. The penalised cost never decreases along the
     * tour, so the full tour cannot beat {@code bound} either.
     */
    public static TourResult evaluate(List<Integer> tour, int t0, Router router, long bound) {
        List<Leg> legs = new ArrayList<>();
        int time = t0;
        int waiting = 0;
        int ride = 0;
        int transfers = 0;
        int infeasible = 0;
        for (int i = 0; i + 1 < tour.size(); i++) {
            Leg leg = router.leg(tour.get(i), tour.get(i + 1), time);
            legs.add(leg);
            if (leg.feasible()) {
                time = leg.arrival();
                waiting += leg.waiting();
                ride += leg.ride();
                transfers += leg.transfers();
            } else {
                infeasible++;
            }
            if (infeasible * TourResult.PENALTY + (time - t0) >= bound) {
                return null;
            }
        }
        return new TourResult(List.copyOf(tour), legs, t0, time, waiting, ride, transfers, infeasible);
    }
}
