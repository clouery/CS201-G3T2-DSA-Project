package tdtsp.tour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tdtsp.routing.Router;

/**
 * 2-opt and insertion (relocate) for the open-path tour, with full re-evaluation (plan §5.6).
 *
 * <p>Costs are time-dependent and asymmetric, so the classic 2-opt delta is invalid:
 * every candidate tour is re-simulated with {@link TourEvaluator}. Wrap the router
 * in a {@link tdtsp.routing.MemoRouter} so unchanged prefixes are cache hits.
 * The start city stays first.
 *
 * <p>Time: O(n² × n × router cost) per pass.
 */
public final class LocalSearch {

    private LocalSearch() {
    }

    /** Improves {@code tour} until no move helps or {@code maxPasses} passes are done. */
    public static TourResult improve(List<Integer> tour, int t0, Router router, int maxPasses) {
        List<Integer> current = new ArrayList<>(tour);
        TourResult best = TourEvaluator.evaluate(current, t0, router);
        int n = current.size();

        for (int pass = 0; pass < maxPasses; pass++) {
            boolean improved = false;

            // 2-opt: reverse current[i..j]
            for (int i = 1; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    List<Integer> candidate = new ArrayList<>(current);
                    Collections.reverse(candidate.subList(i, j + 1));
                    TourResult r = TourEvaluator.evaluate(candidate, t0, router, best.cost());
                    if (r != null) {
                        current = candidate;
                        best = r;
                        improved = true;
                    }
                }
            }

            // Insertion: move current[i] to position j
            for (int i = 1; i < n; i++) {
                for (int j = 1; j < n; j++) {
                    if (i == j) {
                        continue;
                    }
                    List<Integer> candidate = new ArrayList<>(current);
                    candidate.add(j, candidate.remove(i));
                    TourResult r = TourEvaluator.evaluate(candidate, t0, router, best.cost());
                    if (r != null) {
                        current = candidate;
                        best = r;
                        improved = true;
                    }
                }
            }

            if (!improved) {
                break;
            }
        }
        return best;
    }
}
