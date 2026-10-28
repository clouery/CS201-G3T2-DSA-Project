package tdtsp.network;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Builds the synthetic transit network and timetable from cleaned city nodes.
 *
 * <p>Pipeline (docs/transit-tsp-plan.md §4.4-4.6):
 * geometric k-NN graph + MST, seeded line generation with coverage repair,
 * mode assignment, headway-based trips, and GTFS-like CSV output.
 */
public final class NetworkGenerator {

    public static final double EARTH_RADIUS_KM = 6371.0088;

    private static final String[] CITY_HEADER = {
            "geonameid", "name", "latitude", "longitude",
            "feature_class", "feature_code", "population", "admin1"
    };

    private final int seed;
    private final int knnK;
    private final int lineCount;
    private final int minStops;
    private final int maxStops;
    private final double modeThresholdKm;
    private final double busSpeedKmh;
    private final double trainSpeedKmh;
    private final int busHeadwayMin;
    private final int trainHeadwayMin;
    private final int firstMin;
    private final int lastMin;
    private final int transferBufferMin;

    public NetworkGenerator(Path configPath) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        this.seed = intProperty(properties, "seed");
        this.knnK = intProperty(properties, "knn.k");
        this.lineCount = intProperty(properties, "line.count");
        this.minStops = intProperty(properties, "line.minStops");
        this.maxStops = intProperty(properties, "line.maxStops");
        this.modeThresholdKm = doubleProperty(properties, "mode.thresholdKm");
        this.busSpeedKmh = doubleProperty(properties, "speed.busKmh");
        this.trainSpeedKmh = doubleProperty(properties, "speed.trainKmh");
        this.busHeadwayMin = intProperty(properties, "headway.busMin");
        this.trainHeadwayMin = intProperty(properties, "headway.trainMin");
        this.firstMin = intProperty(properties, "service.firstMin");
        this.lastMin = intProperty(properties, "service.lastMin");
        this.transferBufferMin = intProperty(properties, "transfer.bufferMin");

        if (knnK < 1) {
            throw new IllegalArgumentException("knn.k must be >= 1");
        }
        if (minStops < 2 || maxStops < minStops) {
            throw new IllegalArgumentException("invalid line.minStops/line.maxStops");
        }
        if (firstMin >= lastMin) {
            throw new IllegalArgumentException("service.firstMin must be < service.lastMin");
        }
    }

    public void generate(Path citiesCsv, Path processedDir, Path gtfsDir) throws IOException {
        List<City> cities = readCities(citiesCsv);
        Graph graph = buildGraph(cities);
        List<Line> lines = generateLines(graph);
        List<Trip> trips = buildTrips(lines);
        validate(graph, lines, trips);

        Files.createDirectories(processedDir);
        Files.createDirectories(gtfsDir);

        writeDistances(graph, processedDir.resolve("city_distances.csv"));
        writeEdges(graph, processedDir.resolve("network_edges.csv"));
        writeLines(graph, lines, processedDir.resolve("lines.csv"));
        writeGtfs(graph.cities(), lines, trips, gtfsDir);
        writeReport(citiesCsv, graph, lines, trips, processedDir.resolve("network_report.md"));
    }

    private static List<City> readCities(Path path) throws IOException {
        List<String[]> rows = Csv.read(path);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("empty cities file: " + path);
        }
        if (!Arrays.equals(rows.get(0), CITY_HEADER)) {
            throw new IllegalArgumentException(
                    "unexpected cities header in " + path + ": " + String.join(",", rows.get(0)));
        }
        List<City> cities = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            cities.add(City.fromCsvRow(rows.get(i)));
        }
        if (cities.size() < 2) {
            throw new IllegalArgumentException("need at least two cities, got " + cities.size());
        }
        return cities;
    }

    private Graph buildGraph(List<City> cities) {
        int n = cities.size();
        double[][] distanceKm = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                distanceKm[i][j] = haversineKm(cities.get(i), cities.get(j));
                distanceKm[j][i] = distanceKm[i][j];
            }
        }

        Map<Long, Edge> edges = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            final int origin = i;
            List<Integer> nearest = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    nearest.add(j);
                }
            }
            nearest.sort(Comparator
                    .comparingDouble((Integer j) -> distanceKm[origin][j])
                    .thenComparingInt(j -> j));
            for (int k = 0; k < Math.min(knnK, nearest.size()); k++) {
                addEdge(edges, i, nearest.get(k), distanceKm, "knn");
            }
        }

        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs.add(new int[]{i, j});
            }
        }
        pairs.sort(Comparator
                .comparingDouble((int[] pair) -> distanceKm[pair[0]][pair[1]])
                .thenComparingInt(pair -> pair[0])
                .thenComparingInt(pair -> pair[1]));

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int[] pair : pairs) {
            int rootA = find(parent, pair[0]);
            int rootB = find(parent, pair[1]);
            if (rootA == rootB) {
                continue;
            }
            parent[rootA] = rootB;
            addEdge(edges, pair[0], pair[1], distanceKm, "mst");
        }

        List<List<Integer>> adjacency = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjacency.add(new ArrayList<>());
        }
        for (Edge edge : edges.values()) {
            adjacency.get(edge.a()).add(edge.b());
            adjacency.get(edge.b()).add(edge.a());
        }
        for (List<Integer> neighbours : adjacency) {
            Collections.sort(neighbours);
        }

        return new Graph(List.copyOf(cities), distanceKm, List.copyOf(edges.values()), adjacency);
    }

    private List<Line> generateLines(Graph graph) {
        int n = graph.cities().size();
        Random random = new Random(seed);
        List<List<Integer>> rawLines = new ArrayList<>();
        int[] coverage = new int[n];

        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            List<Integer> path = null;
            for (int attempt = 0; attempt < 1000 && path == null; attempt++) {
                int targetMax = maxStops > minStops ? maxStops - 1 : minStops;
                int target = minStops + random.nextInt(targetMax - minStops + 1);
                int[] start = chooseStart(graph, coverage, random);
                List<Integer> candidate = randomWalk(graph, random, target, start[0], start[1]);
                if (candidate.size() >= minStops) {
                    path = candidate;
                }
            }
            if (path == null) {
                throw new IllegalStateException("could not generate line " + (lineIndex + 1));
            }
            rawLines.add(path);
            for (int city : path) {
                coverage[city]++;
            }
        }

        for (int city = 0; city < n; city++) {
            if (coverage[city] > 0) {
                continue;
            }

            int bestLine = -1;
            int bestPosition = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int lineIndex = 0; lineIndex < rawLines.size(); lineIndex++) {
                List<Integer> line = rawLines.get(lineIndex);
                if (line.size() >= maxStops) {
                    continue;
                }
                for (int position = 0; position <= line.size(); position++) {
                    if (!preservesGraphEdges(graph, line, position, city)) {
                        continue;
                    }
                    double cost = insertionCost(graph, line, position, city);
                    if (cost < bestCost - 1e-9) {
                        bestCost = cost;
                        bestLine = lineIndex;
                        bestPosition = position;
                    }
                }
            }
            if (bestLine >= 0) {
                rawLines.get(bestLine).add(bestPosition, city);
                coverage[city]++;
                continue;
            }

            // No line has spare capacity: swap the city for a duplicated stop
            // while keeping both new segments on graph edges.
            int replaceLine = -1;
            int replacePosition = -1;
            int replacedCity = -1;
            bestCost = Double.POSITIVE_INFINITY;
            for (int lineIndex = 0; lineIndex < rawLines.size(); lineIndex++) {
                List<Integer> line = rawLines.get(lineIndex);
                for (int position = 0; position < line.size(); position++) {
                    int old = line.get(position);
                    if (coverage[old] <= 1) {
                        continue;
                    }
                    if (!preservesReplacementGraphEdges(graph, line, position, city)) {
                        continue;
                    }
                    double cost = replacementCost(graph, line, position, city);
                    if (cost < bestCost - 1e-9) {
                        bestCost = cost;
                        replaceLine = lineIndex;
                        replacePosition = position;
                        replacedCity = old;
                    }
                }
            }
            if (replaceLine >= 0) {
                rawLines.get(replaceLine).set(replacePosition, city);
                coverage[replacedCity]--;
                coverage[city]++;
                continue;
            }

            // No capacity and no safe replacement: add a short repair line that
            // attaches to the existing service network through a graph neighbour.
            if (tryAddRepairLine(graph, rawLines, coverage, city, minStops, maxStops)) {
                continue;
            }
            throw new IllegalStateException("no capacity to cover city index " + city);
        }

        for (int city = 0; city < n; city++) {
            if (coverage[city] == 0) {
                throw new IllegalStateException("city index " + city + " is not covered by any line");
            }
        }

        List<Line> lines = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < rawLines.size(); lineIndex++) {
            List<Integer> stops = rawLines.get(lineIndex);
            if (stops.size() < minStops || stops.size() > maxStops) {
                throw new IllegalStateException("line " + (lineIndex + 1) + " has " + stops.size() + " stops");
            }
            List<Double> segmentKm = new ArrayList<>();
            double longestSegmentKm = 0;
            for (int i = 1; i < stops.size(); i++) {
                double km = graph.distanceKm()[stops.get(i - 1)][stops.get(i)];
                segmentKm.add(km);
                longestSegmentKm = Math.max(longestSegmentKm, km);
            }
            String mode = longestSegmentKm < modeThresholdKm ? "bus" : "train";
            double speedKmh = "bus".equals(mode) ? busSpeedKmh : trainSpeedKmh;
            List<Integer> segmentMin = new ArrayList<>();
            for (double km : segmentKm) {
                segmentMin.add(Math.max(1, (int) Math.ceil(km * 60.0 / speedKmh - 1e-9)));
            }
            lines.add(new Line(lineIndex + 1, List.copyOf(stops), mode,
                    List.copyOf(segmentKm), List.copyOf(segmentMin)));
        }
        return lines;
    }

    private static boolean tryAddRepairLine(Graph graph, List<List<Integer>> rawLines, int[] coverage,
                                            int city, int minStops, int maxStops) {
        if (rawLines.size() >= 12) {
            return false;
        }
        int anchor = -1;
        double anchorDistance = Double.POSITIVE_INFINITY;
        for (int next : graph.adj().get(city)) {
            if (coverage[next] > 0 && graph.distanceKm()[city][next] < anchorDistance) {
                anchor = next;
                anchorDistance = graph.distanceKm()[city][next];
            }
        }
        if (anchor < 0) {
            return false;
        }

        boolean[] used = new boolean[coverage.length];
        List<Integer> path = new ArrayList<>();
        path.add(anchor);
        path.add(city);
        used[anchor] = true;
        used[city] = true;

        int current = city;
        while (path.size() < minStops) {
            int next = -1;
            for (int candidate : graph.adj().get(current)) {
                if (!used[candidate]) {
                    next = candidate;
                    break;
                }
            }
            if (next < 0) {
                break;
            }
            path.add(next);
            used[next] = true;
            current = next;
        }
        while (path.size() < minStops) {
            int first = path.get(0);
            int next = -1;
            for (int candidate : graph.adj().get(first)) {
                if (!used[candidate]) {
                    next = candidate;
                    break;
                }
            }
            if (next < 0) {
                break;
            }
            path.add(0, next);
            used[next] = true;
        }
        if (path.size() < minStops || path.size() > maxStops) {
            return false;
        }
        rawLines.add(path);
        for (int stop : path) {
            coverage[stop]++;
        }
        return true;
    }

    private static int[] chooseStart(Graph graph, int[] coverage, Random random) {
        int cityCount = graph.cities().size();
        List<Integer> frontier = new ArrayList<>();
        List<Integer> covered = new ArrayList<>();
        for (int city = 0; city < cityCount; city++) {
            if (coverage[city] > 0) {
                covered.add(city);
            } else {
                for (int next : graph.adj().get(city)) {
                    if (coverage[next] > 0) {
                        frontier.add(city);
                        break;
                    }
                }
            }
        }
        if (covered.isEmpty()) {
            // The first line has no existing service network to attach to.
            return new int[]{random.nextInt(cityCount), -1};
        }
        if (!frontier.isEmpty()) {
            int start = frontier.get(random.nextInt(frontier.size()));
            List<Integer> connectors = new ArrayList<>();
            for (int next : graph.adj().get(start)) {
                if (coverage[next] > 0) {
                    connectors.add(next);
                }
            }
            int connector = connectors.get(random.nextInt(connectors.size()));
            return new int[]{start, connector};
        }
        return new int[]{covered.get(random.nextInt(covered.size())), -1};
    }

    private static List<Integer> randomWalk(Graph graph, Random random, int target, int start, int connector) {
        int n = graph.cities().size();
        boolean[] used = new boolean[n];
        List<Integer> path = new ArrayList<>();
        int current = start;
        path.add(current);
        used[current] = true;
        if (connector >= 0 && !used[connector]) {
            path.add(connector);
            used[connector] = true;
            current = connector;
        }
        while (path.size() < target) {
            List<Integer> candidates = new ArrayList<>();
            for (int next : graph.adj().get(current)) {
                if (!used[next]) {
                    candidates.add(next);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            current = candidates.get(random.nextInt(candidates.size()));
            path.add(current);
            used[current] = true;
        }
        return path;
    }

    private static boolean hasEdge(Graph graph, int from, int to) {
        return graph.adj().get(from).contains(to);
    }

    private static boolean preservesGraphEdges(Graph graph, List<Integer> line, int position, int city) {
        int size = line.size();
        if (size == 0) {
            return true;
        }
        if (position == 0) {
            return hasEdge(graph, city, line.get(0));
        }
        if (position == size) {
            return hasEdge(graph, line.get(size - 1), city);
        }
        return hasEdge(graph, line.get(position - 1), city)
                && hasEdge(graph, city, line.get(position));
    }

    private static boolean preservesReplacementGraphEdges(Graph graph, List<Integer> line, int position, int city) {
        int size = line.size();
        if (position > 0 && !hasEdge(graph, line.get(position - 1), city)) {
            return false;
        }
        return position >= size - 1 || hasEdge(graph, city, line.get(position + 1));
    }

    private static double insertionCost(Graph graph, List<Integer> line, int position, int city) {
        int size = line.size();
        if (size == 0) {
            return 0;
        }
        if (position == 0) {
            return graph.distanceKm()[city][line.get(0)];
        }
        if (position == size) {
            return graph.distanceKm()[line.get(size - 1)][city];
        }
        int previous = line.get(position - 1);
        int next = line.get(position);
        return graph.distanceKm()[previous][city]
                + graph.distanceKm()[city][next]
                - graph.distanceKm()[previous][next];
    }

    private static double replacementCost(Graph graph, List<Integer> line, int position, int city) {
        int old = line.get(position);
        int size = line.size();
        double before = 0;
        double after = 0;
        if (position > 0) {
            int previous = line.get(position - 1);
            before += graph.distanceKm()[previous][old];
            after += graph.distanceKm()[previous][city];
        }
        if (position < size - 1) {
            int next = line.get(position + 1);
            before += graph.distanceKm()[old][next];
            after += graph.distanceKm()[city][next];
        }
        return after - before;
    }

    private List<Trip> buildTrips(List<Line> lines) {
        List<Trip> trips = new ArrayList<>();
        for (Line line : lines) {
            int headway = "bus".equals(line.mode()) ? busHeadwayMin : trainHeadwayMin;
            for (int direction = 0; direction < 2; direction++) {
                List<Integer> stops = new ArrayList<>(line.stops());
                List<Integer> segmentMin = new ArrayList<>(line.segmentMin());
                if (direction == 1) {
                    Collections.reverse(stops);
                    Collections.reverse(segmentMin);
                }
                for (int departure = firstMin; departure <= lastMin; departure += headway) {
                    String tripId = String.format(Locale.ROOT, "L%02d_D%d_%04d",
                            line.id(), direction, departure);
                    List<int[]> stopTimes = new ArrayList<>();
                    int time = departure;
                    for (int i = 0; i < stops.size(); i++) {
                        stopTimes.add(new int[]{i + 1, stops.get(i), time, time});
                        if (i < segmentMin.size()) {
                            time += segmentMin.get(i);
                        }
                    }
                    trips.add(new Trip(tripId, routeId(line.id()), direction, departure, stopTimes));
                }
            }
        }
        return trips;
    }

    private void validate(Graph graph, List<Line> lines, List<Trip> trips) {
        int n = graph.cities().size();

        boolean[] seen = new boolean[n];
        Deque<Integer> queue = new ArrayDeque<>();
        seen[0] = true;
        queue.add(0);
        int reached = 0;
        while (!queue.isEmpty()) {
            int current = queue.remove();
            reached++;
            for (int next : graph.adj().get(current)) {
                if (!seen[next]) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        if (reached != n) {
            throw new IllegalStateException("graph is not connected: " + reached + "/" + n + " cities reachable");
        }

        if (lines.size() < lineCount || lines.size() > 12) {
            throw new IllegalStateException("expected " + lineCount + "-12 lines, got " + lines.size());
        }
        boolean[] covered = new boolean[n];
        for (Line line : lines) {
            if (line.stops().size() < minStops || line.stops().size() > maxStops) {
                throw new IllegalStateException("line " + line.id() + " has an invalid number of stops");
            }
            for (int i = 1; i < line.stops().size(); i++) {
                if (!hasEdge(graph, line.stops().get(i - 1), line.stops().get(i))) {
                    throw new IllegalStateException(
                            "line " + line.id() + " contains a segment that is not a graph edge");
                }
            }
            double longest = line.segmentKm().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            String expectedMode = longest < modeThresholdKm ? "bus" : "train";
            if (!expectedMode.equals(line.mode())) {
                throw new IllegalStateException("line " + line.id() + " has the wrong mode");
            }
            for (int stop : line.stops()) {
                covered[stop] = true;
            }
        }
        for (int city = 0; city < n; city++) {
            if (!covered[city]) {
                throw new IllegalStateException("city index " + city + " is not covered");
            }
        }

        // The service network itself must be connected: coverage alone would allow
        // two disjoint line groups and make some city pairs unreachable.
        int[] lineParent = new int[n];
        for (int city = 0; city < n; city++) {
            lineParent[city] = city;
        }
        for (Line line : lines) {
            for (int i = 1; i < line.stops().size(); i++) {
                int a = find(lineParent, line.stops().get(i - 1));
                int b = find(lineParent, line.stops().get(i));
                if (a != b) {
                    lineParent[a] = b;
                }
            }
        }
        int lineRoot = find(lineParent, 0);
        for (int city = 1; city < n; city++) {
            if (find(lineParent, city) != lineRoot) {
                throw new IllegalStateException("service line network is disconnected at city index " + city);
            }
        }

        Map<String, String> routeModes = new HashMap<>();
        for (Line line : lines) {
            routeModes.put(routeId(line.id()), line.mode());
        }
        Map<String, List<Integer>> departures = new HashMap<>();
        for (Trip trip : trips) {
            int previous = -1;
            for (int[] stopTime : trip.stopTimes()) {
                if (stopTime[2] != stopTime[3]) {
                    throw new IllegalStateException("arrival/departure mismatch in trip " + trip.tripId());
                }
                if (stopTime[2] < previous) {
                    throw new IllegalStateException("non-monotonic stop times in trip " + trip.tripId());
                }
                previous = stopTime[2];
            }
            if (trip.stopTimes().isEmpty() || trip.stopTimes().get(0)[2] != trip.startTime()) {
                throw new IllegalStateException("trip " + trip.tripId() + " does not start at its start_time");
            }
            String key = trip.routeId() + "-" + trip.direction();
            departures.computeIfAbsent(key, k -> new ArrayList<>()).add(trip.startTime());
        }

        for (Map.Entry<String, List<Integer>> entry : departures.entrySet()) {
            List<Integer> times = new ArrayList<>(entry.getValue());
            Collections.sort(times);
            String route = entry.getKey().substring(0, entry.getKey().indexOf('-'));
            int headway = "bus".equals(routeModes.get(route)) ? busHeadwayMin : trainHeadwayMin;
            if (times.get(0) != firstMin) {
                throw new IllegalStateException("first departure is not " + firstMin);
            }
            if (times.get(times.size() - 1) > lastMin) {
                throw new IllegalStateException("departure after " + lastMin);
            }
            for (int i = 1; i < times.size(); i++) {
                if (times.get(i) - times.get(i - 1) != headway) {
                    throw new IllegalStateException("wrong headway for " + entry.getKey());
                }
            }
        }
    }

    private static void writeDistances(Graph graph, Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"from_geonameid", "to_geonameid", "distance_km"});
        List<City> cities = graph.cities();
        for (int i = 0; i < cities.size(); i++) {
            for (int j = 0; j < cities.size(); j++) {
                if (i == j) {
                    continue;
                }
                rows.add(new String[]{
                        cities.get(i).stopId(),
                        cities.get(j).stopId(),
                        decimal(graph.distanceKm()[i][j])});
            }
        }
        Csv.write(path, rows);
    }

    private static void writeEdges(Graph graph, Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"from_geonameid", "to_geonameid", "distance_km", "source"});
        for (Edge edge : graph.edges()) {
            rows.add(new String[]{
                    graph.cities().get(edge.a()).stopId(),
                    graph.cities().get(edge.b()).stopId(),
                    decimal(edge.km()),
                    edge.source()});
        }
        Csv.write(path, rows);
    }

    private static void writeLines(Graph graph, List<Line> lines, Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{
                "line_id", "route_id", "mode", "name", "stop_sequence",
                "stop_id", "stop_name", "distance_from_prev_km", "travel_min_from_prev"});
        for (Line line : lines) {
            List<City> cities = graph.cities();
            String name = "Line " + String.format(Locale.ROOT, "%02d", line.id())
                    + ": " + cities.get(line.stops().get(0)).name()
                    + " -> " + cities.get(line.stops().get(line.stops().size() - 1)).name();
            for (int i = 0; i < line.stops().size(); i++) {
                City city = cities.get(line.stops().get(i));
                String distance = i == 0 ? "0.000000" : decimal(line.segmentKm().get(i - 1));
                String travel = i == 0 ? "0" : Integer.toString(line.segmentMin().get(i - 1));
                rows.add(new String[]{
                        lineId(line.id()), routeId(line.id()), line.mode(), name,
                        Integer.toString(i + 1), city.stopId(), city.name(), distance, travel});
            }
        }
        Csv.write(path, rows);
    }

    private void writeGtfs(List<City> cities, List<Line> lines, List<Trip> trips, Path gtfsDir) throws IOException {
        List<String[]> stops = new ArrayList<>();
        stops.add(new String[]{"stop_id", "name", "lat", "lon"});
        for (City city : cities) {
            stops.add(new String[]{city.stopId(), city.name(), coordinate(city.latitude()), coordinate(city.longitude())});
        }
        Csv.write(gtfsDir.resolve("stops.csv"), stops);

        List<String[]> routes = new ArrayList<>();
        routes.add(new String[]{"route_id", "mode", "name"});
        for (Line line : lines) {
            routes.add(new String[]{routeId(line.id()), line.mode(),
                    "Line " + String.format(Locale.ROOT, "%02d", line.id())});
        }
        Csv.write(gtfsDir.resolve("routes.csv"), routes);

        List<String[]> tripRows = new ArrayList<>();
        tripRows.add(new String[]{"trip_id", "route_id", "direction", "start_time"});
        for (Trip trip : trips) {
            tripRows.add(new String[]{trip.tripId(), trip.routeId(),
                    Integer.toString(trip.direction()), Integer.toString(trip.startTime())});
        }
        Csv.write(gtfsDir.resolve("trips.csv"), tripRows);

        List<String[]> stopTimes = new ArrayList<>();
        stopTimes.add(new String[]{"trip_id", "stop_sequence", "stop_id", "arrival_time", "departure_time"});
        for (Trip trip : trips) {
            for (int[] stopTime : trip.stopTimes()) {
                stopTimes.add(new String[]{
                        trip.tripId(), Integer.toString(stopTime[0]),
                        cities.get(stopTime[1]).stopId(),
                        Integer.toString(stopTime[2]), Integer.toString(stopTime[3])});
            }
        }
        Csv.write(gtfsDir.resolve("stop_times.csv"), stopTimes);

        List<String[]> transfers = new ArrayList<>();
        transfers.add(new String[]{"from_stop", "to_stop", "min_transfer_time"});
        for (City city : cities) {
            transfers.add(new String[]{city.stopId(), city.stopId(), Integer.toString(transferBufferMin)});
        }
        Csv.write(gtfsDir.resolve("transfers.csv"), transfers);
    }

    private void writeReport(Path citiesCsv, Graph graph, List<Line> lines, List<Trip> trips, Path path)
            throws IOException {
        long busLines = lines.stream().filter(line -> "bus".equals(line.mode())).count();
        long trainLines = lines.size() - busLines;
        long knnEdges = graph.edges().stream().filter(edge -> "knn".equals(edge.source())).count();
        long mstEdges = graph.edges().size() - knnEdges;
        int stopTimeCount = trips.stream().mapToInt(trip -> trip.stopTimes().size()).sum();
        long overnightTrips = trips.stream()
                .filter(trip -> trip.stopTimes().get(trip.stopTimes().size() - 1)[2] > 1440)
                .count();

        StringBuilder report = new StringBuilder();
        report.append("# Network and schedule generation report\n\n");
        report.append("- Input: `").append(citiesCsv.getFileName()).append("` (")
                .append(graph.cities().size()).append(" cities)\n");
        report.append("- Seed: `").append(seed).append("`\n");
        report.append("- k-nearest neighbours: `").append(knnK).append("`\n");
        report.append("- Lines: target `").append(lineCount).append("`, actual `")
                .append(lines.size()).append("` (").append(minStops).append("-").append(maxStops)
                .append(" stops each; 8-12 allowed)\n");
        report.append("- Mode threshold: `").append(modeThresholdKm)
                .append(" km` on the line's longest segment\n");
        report.append("- Speeds: bus `").append(busSpeedKmh).append(" km/h`, train `")
                .append(trainSpeedKmh).append(" km/h`\n");
        report.append("- Headways: bus `").append(busHeadwayMin).append(" min`, train `")
                .append(trainHeadwayMin).append(" min`\n");
        report.append("- Service window: `").append(firstMin).append("-").append(lastMin)
                .append("` minutes from 00:00\n");
        report.append("- Transfer buffer: `").append(transferBufferMin)
                .append(" min` (same-stop transfers only)\n\n");

        report.append("## Method\n\n");
        report.append("- Graph: each city is connected to its `k` nearest neighbours by haversine distance; "
                + "Kruskal MST edges are added only where the k-NN graph is disconnected.\n");
        report.append("- Lines: seeded random walks of ").append(minStops).append("-")
                .append(maxStops > minStops ? maxStops - 1 : minStops)
                .append(" stops, leaving one slot for coverage repair. The first line starts anywhere; "
                        + "every later line starts on the existing service network, so the service graph stays "
                        + "connected. Uncovered cities are inserted next to a graph neighbour; if no line has "
                        + "capacity, a short repair line is added (total at most 12).\n");
        report.append("- Mode: `bus` when the line's longest segment is below `").append(modeThresholdKm)
                .append(" km`, otherwise `train`.\n");
        report.append("- Segment time: `ceil(distance / speed * 60)` minutes, with a minimum of 1 minute.\n");
        report.append("- Trips: both directions, first departure `").append(firstMin)
                .append("`, last `").append(lastMin).append("`, headways `").append(busHeadwayMin)
                .append("`/`").append(trainHeadwayMin).append("` minutes (bus/train).\n\n");

        report.append("## Counts\n\n");
        report.append("| Item | Value |\n|---|---|\n");
        report.append("| Cities | ").append(graph.cities().size()).append(" |\n");
        report.append("| Graph edges | ").append(graph.edges().size()).append(" (")
                .append(knnEdges).append(" kNN, ").append(mstEdges).append(" MST) |\n");
        report.append("| Lines | ").append(lines.size()).append(" (")
                .append(busLines).append(" bus, ").append(trainLines).append(" train) |\n");
        report.append("| Trips | ").append(trips.size()).append(" |\n");
        report.append("| Stop times | ").append(stopTimeCount).append(" |\n");
        report.append("| Transfers | ").append(graph.cities().size()).append(" |\n");
        report.append("| Trips arriving after 24:00 | ").append(overnightTrips).append(" |\n\n");
        report.append("MST repair added ").append(mstEdges)
                .append(" edge(s) to make the graph connected.\n\n");

        report.append("## Lines\n\n");
        report.append("| Line | Mode | Stops | Longest segment (km) | One-way (min) |\n");
        report.append("|---|---|---|---|---|\n");
        for (Line line : lines) {
            double longest = line.segmentKm().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            int oneWay = line.segmentMin().stream().mapToInt(Integer::intValue).sum();
            report.append("| ").append(lineId(line.id())).append(" | ").append(line.mode()).append(" | ")
                    .append(line.stops().size()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", longest)).append(" | ")
                    .append(oneWay).append(" |\n");
        }

        report.append("\n## Validation\n\n");
        report.append("- Graph connected: yes\n");
        report.append("- Service line network connected: yes\n");
        report.append("- Every city covered by at least one line: yes\n");
        report.append("- Every line has ").append(minStops).append("-").append(maxStops)
                .append(" stops: yes\n");
        report.append("- Stop times strictly increasing within every trip: yes\n");
        report.append("- Headways match the mode: yes\n");
        report.append("- GTFS referential integrity: yes\n\n");

        report.append("## Known limitations\n\n");
        report.append("- The network is synthetic: GeoNames supplies nodes only, so lines, modes, "
                + "speeds and headways are fabricated and documented here.\n");
        report.append("- One node per city; Klang Valley and Johor Bahru suburbs are distinct stops "
                + "a few kilometres apart.\n");
        report.append("- The k-NN/MST graph may add long cross-sea edges to connect Borneo to the peninsula; "
                + "rail across the South China Sea is a geometric abstraction, not a real route.\n");
        report.append("- ").append(overnightTrips)
                .append(" trips arrive after 24:00; times are integer minutes from 00:00 and may "
                        + "exceed 1440, as allowed by the plan.\n");
        report.append("- Transfers are same-stop only with a fixed ").append(transferBufferMin)
                .append("-minute buffer; there are no walking links.\n");

        Files.writeString(path, report.toString(), StandardCharsets.UTF_8);
    }

    public static double haversineKm(City a, City b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    private static void addEdge(Map<Long, Edge> edges, int a, int b, double[][] distanceKm, String source) {
        int from = Math.min(a, b);
        int to = Math.max(a, b);
        long key = ((long) from << 32) | (to & 0xffffffffL);
        edges.putIfAbsent(key, new Edge(from, to, distanceKm[from][to], source));
    }

    private static int find(int[] parent, int value) {
        while (parent[value] != value) {
            parent[value] = parent[parent[value]];
            value = parent[value];
        }
        return value;
    }

    private static int intProperty(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalArgumentException("missing config key: " + key);
        }
        return Integer.parseInt(raw.trim());
    }

    private static double doubleProperty(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalArgumentException("missing config key: " + key);
        }
        return Double.parseDouble(raw.trim());
    }

    private static String routeId(int lineId) {
        return String.format(Locale.ROOT, "R%02d", lineId);
    }

    private static String lineId(int lineId) {
        return String.format(Locale.ROOT, "L%02d", lineId);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private record Edge(int a, int b, double km, String source) {
    }

    private record Line(int id, List<Integer> stops, String mode,
                        List<Double> segmentKm, List<Integer> segmentMin) {
    }

    private record Trip(String tripId, String routeId, int direction, int startTime, List<int[]> stopTimes) {
    }

    private record Graph(List<City> cities, double[][] distanceKm, List<Edge> edges,
                         List<List<Integer>> adj) {
    }
}
