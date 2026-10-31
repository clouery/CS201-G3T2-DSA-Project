package tdtsp.experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tdtsp.routing.ConnectionScan;
import tdtsp.routing.Edge;
import tdtsp.routing.Leg;
import tdtsp.routing.MemoRouter;
import tdtsp.routing.Router;
import tdtsp.routing.TimeDependentDijkstra;
import tdtsp.routing.TimeDependentDijkstra.Objective;
import tdtsp.tour.NearestNeighbourTour;
import tdtsp.tour.TourEvaluator;
import tdtsp.tour.TourResult;

/**
 * RQ1 - Heuristic gap (plan §6): how much later do min-wait and min-ride arrive than
 * min-arrival, and when do they fail? Varies the headway at the main configuration.
 */
final class Exp1Heuristics {

    static final String ID = "exp1_heuristics";
    private static final List<String> MODES = List.of("min_arrival", "min_wait", "min_ride");

    private Exp1Heuristics() {
    }

    static void run(Path dir) throws Exception {
        ExperimentConfig cfg = new ExperimentConfig(dir.resolve("config.properties"));
        int n = cfg.integer("cities");
        int factor = cfg.integer("trainHeadwayFactor");
        int buffer = cfg.integer("buffer");
        int days = cfg.integer("days");
        int queryCount = cfg.integer("queries");
        int startTime = cfg.integer("startTime");
        List<Integer> headways = cfg.integers("headways");
        List<Integer> seeds = cfg.integers("seeds");

        Table results = new Table("headway", "seed", "mode", "queries", "failed", "pct_optimal",
                "mean_gap_min", "median_gap_min", "max_gap_min", "mean_wait_min", "mean_ride_min",
                "mean_transfers", "tour_elapsed_h", "tour_infeasible_legs");

        for (int h : headways) {
            for (int seed : seeds) {
                Instance inst = Instance.generate(n, seed, h, h * factor, buffer, days);
                System.out.printf("  %s headway=%d seed=%d%n", ID, h, seed);
                List<List<Edge>> adj = inst.network().buildAdjacency();
                Map<String, Router> routers = new LinkedHashMap<>();
                routers.put("min_arrival",
                        new ConnectionScan(inst.network().connections(), inst.network().numStops(), buffer));
                routers.put("min_wait", new TimeDependentDijkstra(adj, buffer, Objective.WAIT));
                routers.put("min_ride", new TimeDependentDijkstra(adj, buffer, Objective.RIDE));

                Map<String, List<Leg>> legs = new LinkedHashMap<>();
                List<Leg> reference = new ArrayList<>();
                for (String m : MODES) {
                    legs.put(m, new ArrayList<>());
                }
                for (int[] q : Approaches.queries(inst.network(), seed, queryCount)) {
                    Leg best = routers.get("min_arrival").leg(q[0], q[1], q[2]);
                    if (!best.feasible()) {
                        continue;
                    }
                    reference.add(best);
                    for (String m : MODES) {
                        legs.get(m).add(routers.get(m).leg(q[0], q[1], q[2]));
                    }
                }

                int start = inst.city(cfg.string("startCity"));
                List<Integer> cities = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    cities.add(i);
                }
                for (String m : MODES) {
                    List<Double> gaps = new ArrayList<>();
                    int failed = 0;
                    double wait = 0;
                    double ride = 0;
                    double transfers = 0;
                    for (int i = 0; i < reference.size(); i++) {
                        Leg leg = legs.get(m).get(i);
                        if (!leg.feasible()) {
                            failed++;
                            continue;
                        }
                        gaps.add((double) (leg.arrival() - reference.get(i).arrival()));
                        wait += leg.waiting();
                        ride += leg.ride();
                        transfers += leg.transfers();
                    }
                    double[] g = gaps.stream().mapToDouble(Double::doubleValue).toArray();
                    long optimal = gaps.stream().filter(x -> x == 0).count();
                    int ok = g.length;

                    Router memo = new MemoRouter(routers.get(m));
                    List<Integer> tour = NearestNeighbourTour.build(cities, start, startTime, memo);
                    TourResult tr = TourEvaluator.evaluate(tour, startTime, memo);

                    results.add(h, seed, m, reference.size(), failed, 100.0 * optimal / Math.max(1, ok),
                            Stats.mean(g), Stats.median(g), g.length == 0 ? Double.NaN : max(g),
                            wait / Math.max(1, ok), ride / Math.max(1, ok), transfers / Math.max(1, ok),
                            tr.elapsed() / 60.0, tr.infeasibleLegs());
                }
            }
        }

        results.write(dir.resolve("results.csv"));
        List<String> metrics = List.of("failed", "pct_optimal", "mean_gap_min", "max_gap_min",
                "mean_wait_min", "mean_ride_min", "mean_transfers", "tour_elapsed_h", "tour_infeasible_legs");
        Table summary = results.summary(List.of("headway", "mode"), metrics);
        summary.write(dir.resolve("summary.csv"));

        List<String> xs = headways.stream().map(String::valueOf).toList();
        SvgChart.line(dir.resolve("plots/gap_vs_headway.svg"), "Leg arrival gap vs min-arrival",
                "Bus headway (min); train = " + factor + "x", "Mean gap (min)", xs,
                series(results, headways, List.of("min_wait", "min_ride"), "mean_gap_min"));
        SvgChart.line(dir.resolve("plots/optimal_vs_headway.svg"), "Legs where the heuristic is optimal",
                "Bus headway (min); train = " + factor + "x", "Optimal legs (%)", xs,
                series(results, headways, List.of("min_wait", "min_ride"), "pct_optimal"));
        SvgChart.line(dir.resolve("plots/tour_vs_headway.svg"), "NN tour duration by leg mode (n = " + n + ")",
                "Bus headway (min); train = " + factor + "x", "Tour duration (h)", xs,
                series(results, headways, MODES, "tour_elapsed_h"));

        String table = summary.markdown("headway", "mode", "pct_optimal_mean", "mean_gap_min_mean",
                "max_gap_min_mean", "mean_wait_min_mean", "mean_ride_min_mean", "tour_elapsed_h_mean",
                "tour_infeasible_legs_mean");
        Readme.write(dir, "Experiment 1 - Heuristic gap (RQ1)",
                "How much later do the min-wait and min-ride leg heuristics arrive than min-arrival, "
                        + "and when do they fail? Leg level: random single-leg queries. Tour level: "
                        + "nearest-neighbour tour from the start city using each mode's legs.",
                cfg, ID,
                List.of("`results.csv`: one row per headway, seed and mode",
                        "`summary.csv`: mean/median/std over seeds per headway and mode",
                        "`plots/gap_vs_headway.svg`, `plots/optimal_vs_headway.svg`, `plots/tour_vs_headway.svg`"),
                table + "\n" + Readme.plot("gap_vs_headway.svg", "gap vs headway")
                        + Readme.plot("optimal_vs_headway.svg", "optimal legs vs headway")
                        + Readme.plot("tour_vs_headway.svg", "tour duration vs headway"),
                """
                - `gap` = heuristic arrival minus earliest arrival for the same query, over queries where \
                min-arrival is feasible. `failed` counts queries where the heuristic found no journey.
                - Min-wait and min-ride are label-setting heuristics on (stop, trip) states ordered by \
                waiting or riding time (plan §5.5); they are not exact optimisers of their own cost.
                - min_arrival legs use CSA; its waiting/riding columns describe the earliest-arrival journey.
                """);
    }

    private static Map<String, double[]> series(Table t, List<Integer> headways, List<String> modes, String metric) {
        Map<String, double[]> s = new LinkedHashMap<>();
        for (String m : modes) {
            s.put(m, headways.stream().mapToDouble(h -> t.mean(metric, "headway", h, "mode", m)).toArray());
        }
        return s;
    }

    private static double max(double[] xs) {
        double m = Double.NEGATIVE_INFINITY;
        for (double x : xs) {
            m = Math.max(m, x);
        }
        return m;
    }
}
