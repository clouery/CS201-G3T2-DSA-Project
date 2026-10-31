package tdtsp.tour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classic static TSP baseline (plan §5.2): nearest-neighbour + 2-opt on a complete,
 * symmetric distance matrix, ignoring the timetable.
 *
 * <p>Costs here are static and symmetric, so the standard O(1) 2-opt delta is valid.
 * The tour is then evaluated on the real timetable with {@link TourEvaluator}.
 */
public final class ClassicTour {

    private ClassicTour() {
    }

    /** Open-path tour from {@code s} over all cities of {@code distKm}. O(n²) per 2-opt pass. */
    public static List<Integer> build(double[][] distKm, int s) {
        int n = distKm.length;
        List<Integer> tour = new ArrayList<>();
        boolean[] used = new boolean[n];
        tour.add(s);
        used[s] = true;
        for (int step = 1; step < n; step++) {
            int last = tour.get(tour.size() - 1);
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (!used[v] && (best < 0 || distKm[last][v] < distKm[last][best])) {
                    best = v;
                }
            }
            tour.add(best);
            used[best] = true;
        }

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    int a = tour.get(i - 1);
                    int b = tour.get(i);
                    int c = tour.get(j);
                    double before = distKm[a][b];
                    double after = distKm[a][c];
                    if (j + 1 < n) {
                        int d = tour.get(j + 1);
                        before += distKm[c][d];
                        after += distKm[b][d];
                    }
                    if (after < before - 1e-9) {
                        Collections.reverse(tour.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
        return tour;
    }

    public static double lengthKm(List<Integer> tour, double[][] distKm) {
        double km = 0;
        for (int i = 0; i + 1 < tour.size(); i++) {
            km += distKm[tour.get(i)][tour.get(i + 1)];
        }
        return km;
    }
}
