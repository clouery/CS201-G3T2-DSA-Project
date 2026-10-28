package tdtsp.tour;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tdtsp.routing.Router;

/**
 * Time-dependent nearest-neighbour tour (plan §5.6, §5.7).
 *
 * <p>Time: O(n² × router cost).
 */
public final class NearestNeighbourTour {

    private NearestNeighbourTour() {
    }

    /**
     * Builds an open-path tour from {@code s}, always moving to the unvisited city
     * with the earliest arrival. Unreachable cities are still appended (with
     * {@link Router#INF} arrival) so the tour stays complete; infeasibility is
     * reported by the evaluator, not here.
     */
    public static List<Integer> build(List<Integer> cities, int s, int t0, Router router) {
        List<Integer> tour = new ArrayList<>();
        tour.add(s);
        int time = t0;
        Set<Integer> unvisited = new LinkedHashSet<>(cities);
        unvisited.remove(s);

        while (!unvisited.isEmpty()) {
            int last = tour.get(tour.size() - 1);
            int bestCity = -1;
            int bestArr = Router.INF;
            for (int v : unvisited) {
                int arr = router.earliestArrival(last, v, time);
                if (bestCity == -1 || arr < bestArr) {
                    bestCity = v;
                    bestArr = arr;
                }
            }
            tour.add(bestCity);
            time = bestArr;
            unvisited.remove(bestCity);
        }
        return tour;
    }
}
