package tdtsp.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import tdtsp.routing.ConnectionScan;
import tdtsp.routing.Edge;
import tdtsp.routing.Router;
import tdtsp.routing.TimeDependentDijkstra;
import tdtsp.routing.TimeExpandedGraph;
import tdtsp.routing.TransitNetwork;

/** Builds the three earliest-arrival routers with timing and size measurements (plan §6 metrics). */
final class Approaches {

    static final String TD_DIJKSTRA = "td_dijkstra";
    static final String CSA = "csa";
    static final String TIME_EXPANDED = "time_expanded";

    /**
     * @param prepMs      preprocessing time (median of 3 builds after a warm-up build)
     * @param structureMb estimated size of the preprocessed structure
     * @param elements    timetable elements: departures (TD), connections (CSA, TE)
     * @param nodes       graph nodes: stops (TD, CSA) or events (TE)
     * @param arcs        graph arcs: line segments (TD), connections (CSA), event arcs (TE)
     */
    record Built(String name, Router router, double prepMs, double structureMb, long elements, long nodes, long arcs) {
    }

    /** Keeps query results observable so timed loops are not optimised away. */
    static volatile long blackhole;

    private Approaches() {
    }

    static List<Built> build(TransitNetwork net, int buffer) {
        List<Built> built = new ArrayList<>();

        Timed<TimeDependentDijkstra> td = timed(() -> new TimeDependentDijkstra(net.buildAdjacency(), buffer));
        List<List<Edge>> adj = net.buildAdjacency();
        long segments = adj.stream().mapToLong(List::size).sum();
        long departures = adj.stream().flatMap(List::stream).mapToLong(e -> e.departures().length).sum();
        built.add(new Built(TD_DIJKSTRA, td.value(), td.ms(), mb(td.value().structureBytes()),
                departures, net.numStops(), segments));

        Timed<ConnectionScan> csa = timed(() -> new ConnectionScan(net.connections(), net.numStops(), buffer));
        built.add(new Built(CSA, csa.value(), csa.ms(), mb(csa.value().structureBytes()),
                csa.value().size(), net.numStops(), csa.value().size()));

        Timed<TimeExpandedGraph> te = timed(() -> new TimeExpandedGraph(net.connections(), net.numStops(), buffer));
        built.add(new Built(TIME_EXPANDED, te.value(), te.ms(), mb(te.value().structureBytes()),
                csa.value().size(), te.value().numNodes(), te.value().numArcs()));
        return built;
    }

    /** Random single-target queries {from, to, time}: distinct stops, departure within day 0's service. */
    static List<int[]> queries(TransitNetwork net, long seed, int count) {
        Random random = new Random(seed);
        List<int[]> queries = new ArrayList<>();
        while (queries.size() < count) {
            int from = random.nextInt(net.numStops());
            int to = random.nextInt(net.numStops());
            if (from != to) {
                queries.add(new int[]{from, to, 360 + random.nextInt(961)});
            }
        }
        return queries;
    }

    /** Mean microseconds per single-target earliest-arrival query. */
    static double meanQueryMicros(Router router, List<int[]> queries) {
        return meanMicros(queries, q -> router.earliestArrival(q[0], q[1], q[2]));
    }

    /** Mean microseconds per one-to-all query (earliest arrival at every stop from {@code q[0]}). */
    static double meanOneToAllMicros(Router router, List<int[]> queries) {
        ToLongFunction<int[]> query = switch (router) {
            case TimeDependentDijkstra td -> q -> td.earliestArrival(q[0], q[2])[q[1]];
            case ConnectionScan csa -> q -> csa.earliestArrival(q[0], q[2])[q[1]];
            case TimeExpandedGraph te -> q -> te.earliestArrival(q[0], q[2])[q[1]];
            default -> throw new IllegalArgumentException("no one-to-all query for " + router);
        };
        return meanMicros(queries, query);
    }

    /** One warm-up pass, then the median of 3 timed passes. */
    private static double meanMicros(List<int[]> queries, ToLongFunction<int[]> query) {
        long sink = 0;
        for (int[] q : queries) {
            sink += query.applyAsLong(q);
        }
        double[] passes = new double[3];
        for (int p = 0; p < passes.length; p++) {
            long start = System.nanoTime();
            for (int[] q : queries) {
                sink += query.applyAsLong(q);
            }
            passes[p] = (System.nanoTime() - start) / 1e3 / queries.size();
        }
        blackhole = sink;
        return Stats.median(passes);
    }

    private record Timed<T>(T value, double ms) {
    }

    private static <T> Timed<T> timed(Supplier<T> build) {
        T value = build.get();
        double[] ms = new double[3];
        for (int i = 0; i < ms.length; i++) {
            long start = System.nanoTime();
            value = build.get();
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        return new Timed<>(value, Stats.median(ms));
    }

    private static double mb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
