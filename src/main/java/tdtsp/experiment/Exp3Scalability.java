package tdtsp.experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tdtsp.routing.ConnectionScan;
import tdtsp.routing.MemoRouter;
import tdtsp.routing.Router;
import tdtsp.tour.LocalSearch;
import tdtsp.tour.NearestNeighbourTour;
import tdtsp.tour.TourEvaluator;
import tdtsp.tour.TourResult;

/**
 * RQ3 - Scalability (plan §6): how do the approaches scale with city count, headway
 * and transfer buffer? One factor is varied at a time around the main configuration.
 */
final class Exp3Scalability {

    static final String ID = "exp3_scalability";

    private Exp3Scalability() {
    }

    /** One instance setting, labelled by the factor it varies. */
    private record Point(String factor, int value, int cities, int headway, int buffer) {
    }

    private record Measured(List<Object[]> approaches, Object[] tour) {
    }

    static void run(Path dir) throws Exception {
        ExperimentConfig cfg = new ExperimentConfig(dir.resolve("config.properties"));
        int baseCities = cfg.integer("baseCities");
        int baseHeadway = cfg.integer("baseHeadway");
        int baseBuffer = cfg.integer("baseBuffer");
        int factor = cfg.integer("trainHeadwayFactor");
        int days = cfg.integer("days");
        int queryCount = cfg.integer("queries");
        int passes = cfg.integer("localSearchPasses");
        int startTime = cfg.integer("startTime");

        List<Point> points = new ArrayList<>();
        for (int n : cfg.integers("cities")) {
            points.add(new Point("cities", n, n, baseHeadway, baseBuffer));
        }
        for (int h : cfg.integers("headways")) {
            points.add(new Point("headway", h, baseCities, h, baseBuffer));
        }
        for (int b : cfg.integers("buffers")) {
            points.add(new Point("buffer", b, baseCities, baseHeadway, b));
        }

        Table results = new Table("factor", "value", "seed", "approach", "prep_ms", "query_us", "structure_mb",
                "timetable_elements", "graph_nodes", "graph_arcs");
        Table tours = new Table("factor", "value", "seed", "nn_elapsed_h", "ls_elapsed_h",
                "nn_infeasible_legs", "ls_infeasible_legs", "ls_ms");
        // The main configuration appears in every factor group; measure it once per seed.
        Map<String, Measured> cache = new HashMap<>();

        for (Point p : points) {
            for (int seed : cfg.integers("seeds")) {
                String key = p.cities() + "/" + p.headway() + "/" + p.buffer() + "/" + seed;
                Measured m = cache.get(key);
                if (m == null) {
                    System.out.printf("  %s cities=%d headway=%d buffer=%d seed=%d%n",
                            ID, p.cities(), p.headway(), p.buffer(), seed);
                    m = measure(Instance.generate(p.cities(), seed, p.headway(), p.headway() * factor,
                            p.buffer(), days), seed, queryCount, passes, cfg.string("startCity"), startTime);
                    cache.put(key, m);
                }
                for (Object[] a : m.approaches()) {
                    results.add(prepend(p, seed, a));
                }
                tours.add(prepend(p, seed, m.tour()));
            }
        }

        results.write(dir.resolve("results.csv"));
        tours.write(dir.resolve("tours.csv"));
        Table summary = results.summary(List.of("factor", "value", "approach"),
                List.of("prep_ms", "query_us", "structure_mb", "timetable_elements", "graph_nodes", "graph_arcs"));
        summary.write(dir.resolve("summary.csv"));
        Table tourSummary = tours.summary(List.of("factor", "value"),
                List.of("nn_elapsed_h", "ls_elapsed_h", "nn_infeasible_legs", "ls_infeasible_legs", "ls_ms"));
        tourSummary.write(dir.resolve("tours_summary.csv"));

        List<String> plots = new ArrayList<>();
        Map<String, String> axis = Map.of(
                "cities", "Cities (n)",
                "headway", "Bus headway (min); train = " + factor + "x",
                "buffer", "Transfer buffer (min)");
        for (String f : List.of("cities", "headway", "buffer")) {
            List<Integer> values = points.stream().filter(p -> p.factor().equals(f)).map(Point::value).toList();
            List<String> xs = values.stream().map(String::valueOf).toList();
            for (String[] chart : new String[][]{
                    {"query_us", "query", "Query time", "Mean time per query (µs)"},
                    {"prep_ms", "prep", "Preprocessing time", "Build time (ms)"},
                    {"structure_mb", "memory", "Preprocessed structure size", "Estimated size (MB)"}}) {
                Map<String, double[]> s = new LinkedHashMap<>();
                for (String a : Exp2Representations.APPROACHES) {
                    s.put(a, values.stream()
                            .mapToDouble(v -> results.mean(chart[0], "factor", f, "value", v, "approach", a))
                            .toArray());
                }
                String file = chart[1] + "_vs_" + f + ".svg";
                SvgChart.line(dir.resolve("plots/" + file), chart[2] + " vs " + f, axis.get(f), chart[3], xs, s);
                plots.add(file);
            }
            Map<String, double[]> s = new LinkedHashMap<>();
            s.put("nearest neighbour", values.stream()
                    .mapToDouble(v -> tours.mean("nn_elapsed_h", "factor", f, "value", v)).toArray());
            s.put("NN + 2-opt/insertion", values.stream()
                    .mapToDouble(v -> tours.mean("ls_elapsed_h", "factor", f, "value", v)).toArray());
            String file = "tour_vs_" + f + ".svg";
            SvgChart.line(dir.resolve("plots/" + file), "Tour duration vs " + f + " (CSA legs)",
                    axis.get(f), "Tour duration (h)", xs, s);
            plots.add(file);
        }

        StringBuilder md = new StringBuilder("### Routing\n\n")
                .append(summary.markdown("factor", "value", "approach", "prep_ms_mean", "query_us_mean",
                        "structure_mb_mean", "timetable_elements_mean", "graph_nodes_mean", "graph_arcs_mean"))
                .append("\n### Tours\n\n")
                .append(tourSummary.markdown("factor", "value", "nn_elapsed_h_mean", "ls_elapsed_h_mean",
                        "nn_infeasible_legs_mean", "ls_infeasible_legs_mean", "ls_ms_mean"))
                .append('\n');
        for (String file : plots) {
            md.append(Readme.plot(file, file.replace(".svg", "").replace('_', ' ')));
        }
        Readme.write(dir, "Experiment 3 - Scalability (RQ3)",
                "How do time-expanded + Dijkstra, CSA and time-dependent Dijkstra scale with the number of "
                        + "cities, the headway and the transfer buffer? Each factor is varied on its own while "
                        + "the others stay at the main configuration (cities = " + baseCities + ", headway = "
                        + baseHeadway + ", buffer = " + baseBuffer + ").",
                cfg, ID,
                List.of("`results.csv`: one row per factor value, seed and approach",
                        "`tours.csv`: tour duration per factor value and seed (CSA legs)",
                        "`summary.csv`, `tours_summary.csv`: mean/median/std over seeds",
                        "`plots/`: query time, preprocessing, memory and tour duration against each factor"),
                md.toString(),
                """
                - Query time, preprocessing and memory are measured as in Experiment 2.
                - Tours start at the start city at the start time; nearest neighbour, then 2-opt and \
                insertion with full re-evaluation for at most `localSearchPasses` passes. `ls_ms` is the \
                local-search time.
                """);
    }

    private static Measured measure(Instance inst, int seed, int queryCount, int passes,
                                    String startCity, int startTime) {
        List<int[]> queries = Approaches.queries(inst.network(), seed, queryCount);
        List<Object[]> approaches = new ArrayList<>();
        for (Approaches.Built b : Approaches.build(inst.network(), inst.buffer())) {
            approaches.add(new Object[]{b.name(), b.prepMs(), Approaches.meanQueryMicros(b.router(), queries),
                    b.structureMb(), b.elements(), b.nodes(), b.arcs()});
        }

        Router router = new MemoRouter(new ConnectionScan(inst.network().connections(),
                inst.network().numStops(), inst.buffer()));
        List<Integer> cities = new ArrayList<>();
        for (int i = 0; i < inst.cities(); i++) {
            cities.add(i);
        }
        int start = inst.city(startCity);
        List<Integer> nnTour = NearestNeighbourTour.build(cities, start, startTime, router);
        TourResult nn = TourEvaluator.evaluate(nnTour, startTime, router);
        long t = System.nanoTime();
        TourResult ls = LocalSearch.improve(nnTour, startTime, router, passes);
        double lsMs = (System.nanoTime() - t) / 1e6;
        return new Measured(approaches, new Object[]{nn.elapsed() / 60.0, ls.elapsed() / 60.0,
                nn.infeasibleLegs(), ls.infeasibleLegs(), lsMs});
    }

    private static Object[] prepend(Point p, int seed, Object[] rest) {
        Object[] row = new Object[3 + rest.length];
        row[0] = p.factor();
        row[1] = p.value();
        row[2] = seed;
        System.arraycopy(rest, 0, row, 3, rest.length);
        return row;
    }
}
