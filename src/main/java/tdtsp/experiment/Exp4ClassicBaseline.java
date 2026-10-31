package tdtsp.experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tdtsp.routing.ConnectionScan;
import tdtsp.routing.MemoRouter;
import tdtsp.routing.Router;
import tdtsp.tour.ClassicTour;
import tdtsp.tour.LocalSearch;
import tdtsp.tour.NearestNeighbourTour;
import tdtsp.tour.TourEvaluator;
import tdtsp.tour.TourResult;

/**
 * RQ4 - Value of the redesign (plan §6): how does the classic static TSP tour perform
 * on the real timetable compared with the time-dependent tours?
 */
final class Exp4ClassicBaseline {

    static final String ID = "exp4_classic_baseline";
    private static final List<String> METHODS = List.of("classic_static", "td_nn", "td_nn_ls");

    private Exp4ClassicBaseline() {
    }

    static void run(Path dir) throws Exception {
        ExperimentConfig cfg = new ExperimentConfig(dir.resolve("config.properties"));
        int headway = cfg.integer("headway");
        int factor = cfg.integer("trainHeadwayFactor");
        int buffer = cfg.integer("buffer");
        int days = cfg.integer("days");
        int passes = cfg.integer("localSearchPasses");
        int startTime = cfg.integer("startTime");
        List<Integer> cityCounts = cfg.integers("cities");

        Table results = new Table("cities", "seed", "method", "elapsed_h", "waiting_h", "ride_h", "transfers",
                "infeasible_legs", "distance_km", "build_ms");
        for (int n : cityCounts) {
            for (int seed : cfg.integers("seeds")) {
                System.out.printf("  %s cities=%d seed=%d%n", ID, n, seed);
                Instance inst = Instance.generate(n, seed, headway, headway * factor, buffer, days);
                double[][] dist = inst.distanceKm();
                int start = inst.city(cfg.string("startCity"));
                List<Integer> cities = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    cities.add(i);
                }

                // A fresh cache per method keeps build times comparable.
                Router classicRouter = router(inst);
                long t = System.nanoTime();
                List<Integer> classicTour = ClassicTour.build(dist, start);
                double classicMs = (System.nanoTime() - t) / 1e6;
                add(results, n, seed, "classic_static",
                        TourEvaluator.evaluate(classicTour, startTime, classicRouter), dist, classicMs);

                Router nnRouter = router(inst);
                t = System.nanoTime();
                List<Integer> nnTour = NearestNeighbourTour.build(cities, start, startTime, nnRouter);
                double nnMs = (System.nanoTime() - t) / 1e6;
                add(results, n, seed, "td_nn", TourEvaluator.evaluate(nnTour, startTime, nnRouter), dist, nnMs);

                Router lsRouter = router(inst);
                t = System.nanoTime();
                List<Integer> lsStart = NearestNeighbourTour.build(cities, start, startTime, lsRouter);
                TourResult ls = LocalSearch.improve(lsStart, startTime, lsRouter, passes);
                double lsMs = (System.nanoTime() - t) / 1e6;
                add(results, n, seed, "td_nn_ls", ls, dist, lsMs);
            }
        }

        results.write(dir.resolve("results.csv"));
        Table summary = results.summary(List.of("cities", "method"), List.of("elapsed_h", "waiting_h", "ride_h",
                "transfers", "infeasible_legs", "distance_km", "build_ms"));
        summary.write(dir.resolve("summary.csv"));

        List<String> xs = cityCounts.stream().map(String::valueOf).toList();
        for (String[] chart : new String[][]{
                {"elapsed_h", "elapsed_vs_cities.svg", "Tour duration on the timetable", "Tour duration (h)"},
                {"waiting_h", "waiting_vs_cities.svg", "Total waiting time", "Waiting (h)"},
                {"distance_km", "distance_vs_cities.svg", "Tour distance (haversine)", "Distance (km)"}}) {
            Map<String, double[]> s = new LinkedHashMap<>();
            for (String m : METHODS) {
                s.put(m, cityCounts.stream().mapToDouble(n -> results.mean(chart[0], "cities", n, "method", m))
                        .toArray());
            }
            SvgChart.line(dir.resolve("plots/" + chart[1]), chart[2], "Cities (n)", chart[3], xs, s);
        }

        String table = summary.markdown("cities", "method", "elapsed_h_mean", "elapsed_h_std", "waiting_h_mean",
                "ride_h_mean", "transfers_mean", "infeasible_legs_mean", "distance_km_mean", "build_ms_mean");
        Readme.write(dir, "Experiment 4 - Classic baseline (RQ4)",
                "How does the classic static TSP tour (nearest neighbour + 2-opt on haversine distance, "
                        + "ignoring the timetable) perform when it is actually travelled on the timetable, "
                        + "compared with the time-dependent tours?",
                cfg, ID,
                List.of("`results.csv`: one row per city count, seed and method",
                        "`summary.csv`: mean/median/std over seeds per city count and method",
                        "`plots/elapsed_vs_cities.svg`, `plots/waiting_vs_cities.svg`, "
                                + "`plots/distance_vs_cities.svg`"),
                table + "\n" + Readme.plot("elapsed_vs_cities.svg", "tour duration vs cities")
                        + Readme.plot("waiting_vs_cities.svg", "waiting vs cities")
                        + Readme.plot("distance_vs_cities.svg", "distance vs cities"),
                """
                - Methods: `classic_static` = static NN + 2-opt on the complete haversine graph, then \
                evaluated leg by leg with earliest-arrival routing (CSA); `td_nn` = time-dependent nearest \
                neighbour; `td_nn_ls` = `td_nn` followed by 2-opt and insertion with full re-evaluation.
                - All tours start at the start city at the start time; the duration is the final arrival \
                minus the start time. Infeasible legs are counted and skipped (plan §5.6).
                - `build_ms` is the time to construct the tour (for `classic_static`, without the timetable \
                evaluation).
                """);
    }

    private static Router router(Instance inst) {
        return new MemoRouter(new ConnectionScan(inst.network().connections(), inst.network().numStops(),
                inst.buffer()));
    }

    private static void add(Table t, int n, int seed, String method, TourResult r, double[][] dist, double ms) {
        t.add(n, seed, method, r.elapsed() / 60.0, r.waiting() / 60.0, r.ride() / 60.0, r.transfers(),
                r.infeasibleLegs(), ClassicTour.lengthKm(r.tour(), dist), ms);
    }
}
