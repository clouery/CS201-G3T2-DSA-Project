package tdtsp.experiment;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RQ2 - Representation trade-off (plan §6): time-expanded + Dijkstra (A) vs
 * time-dependent + CSA (B) in query time, preprocessing time and memory.
 * Time-dependent Dijkstra is reported alongside as the plan's fallback approach.
 */
final class Exp2Representations {

    static final String ID = "exp2_representations";
    static final List<String> APPROACHES =
            List.of(Approaches.TIME_EXPANDED, Approaches.CSA, Approaches.TD_DIJKSTRA);

    private Exp2Representations() {
    }

    static void run(Path dir) throws Exception {
        ExperimentConfig cfg = new ExperimentConfig(dir.resolve("config.properties"));
        int n = cfg.integer("cities");
        int headway = cfg.integer("headway");
        int factor = cfg.integer("trainHeadwayFactor");
        int buffer = cfg.integer("buffer");
        int days = cfg.integer("days");
        int queryCount = cfg.integer("queries");

        Table results = new Table("seed", "approach", "prep_ms", "query_us", "one_to_all_us", "structure_mb",
                "timetable_elements", "graph_nodes", "graph_arcs", "mismatches");
        for (int seed : cfg.integers("seeds")) {
            System.out.printf("  %s seed=%d%n", ID, seed);
            Instance inst = Instance.generate(n, seed, headway, headway * factor, buffer, days);
            List<int[]> queries = Approaches.queries(inst.network(), seed, queryCount);
            List<Approaches.Built> built = Approaches.build(inst.network(), buffer);
            Approaches.Built reference = built.get(1);
            for (Approaches.Built b : built) {
                int mismatches = 0;
                for (int[] q : queries) {
                    if (b.router().earliestArrival(q[0], q[1], q[2])
                            != reference.router().earliestArrival(q[0], q[1], q[2])) {
                        mismatches++;
                    }
                }
                results.add(seed, b.name(), b.prepMs(), Approaches.meanQueryMicros(b.router(), queries),
                        Approaches.meanOneToAllMicros(b.router(), queries), b.structureMb(),
                        b.elements(), b.nodes(), b.arcs(), mismatches);
            }
        }

        results.write(dir.resolve("results.csv"));
        List<String> metrics = List.of("prep_ms", "query_us", "one_to_all_us", "structure_mb",
                "timetable_elements", "graph_nodes", "graph_arcs", "mismatches");
        Table summary = results.summary(List.of("approach"), metrics);
        summary.write(dir.resolve("summary.csv"));

        for (String[] chart : new String[][]{
                {"query_us", "query_time.svg", "Single-target query time", "Mean time per query (µs)"},
                {"one_to_all_us", "one_to_all_time.svg", "One-to-all query time", "Mean time per query (µs)"},
                {"prep_ms", "preprocessing.svg", "Preprocessing time", "Build time (ms)"},
                {"structure_mb", "memory.svg", "Preprocessed structure size", "Estimated size (MB)"}}) {
            Map<String, double[]> s = new LinkedHashMap<>();
            s.put("mean over seeds", APPROACHES.stream().mapToDouble(a -> results.mean(chart[0], "approach", a)).toArray());
            SvgChart.bars(dir.resolve("plots/" + chart[1]), chart[2] + " (n = " + n + ")", "Approach", chart[3],
                    APPROACHES, s);
        }

        String table = summary.markdown("approach", "prep_ms_mean", "query_us_mean", "query_us_std",
                "one_to_all_us_mean", "structure_mb_mean", "timetable_elements_mean", "graph_nodes_mean",
                "graph_arcs_mean", "mismatches_mean");
        Readme.write(dir, "Experiment 2 - Representation trade-off (RQ2)",
                "How do time-expanded graph + Dijkstra (Approach A) and time-dependent graph + CSA "
                        + "(Approach B) compare in query time, preprocessing time and memory on the main "
                        + "configuration? Time-dependent Dijkstra is included as a third reference point.",
                cfg, ID,
                List.of("`results.csv`: one row per seed and approach",
                        "`summary.csv`: mean/median/std over seeds per approach",
                        "`plots/query_time.svg`, `plots/one_to_all_time.svg`, `plots/preprocessing.svg`, "
                                + "`plots/memory.svg`"),
                table + "\n" + Readme.plot("query_time.svg", "query time")
                        + Readme.plot("one_to_all_time.svg", "one-to-all query time")
                        + Readme.plot("preprocessing.svg", "preprocessing time")
                        + Readme.plot("memory.svg", "memory"),
                """
                - `query_us`: single-target earliest-arrival query with early termination (what the tour \
                layer uses). `one_to_all_us`: earliest arrival at every stop from one source.
                - `prep_ms`: building the structure from the loaded timetable (TD: adjacency list with \
                sorted departure arrays; CSA: sorting connections; TE: event graph). Median of 3 builds \
                after a warm-up build.
                - `structure_mb` is estimated from array lengths and JVM object sizes of the preprocessed \
                structure, not measured peak heap, which is too noisy under garbage collection.
                - `graph_nodes`/`graph_arcs`: stops/line segments (TD), stops/connections (CSA), \
                events/event arcs (TE). `mismatches`: queries whose arrival differs from CSA (expected 0).
                """);
    }
}
