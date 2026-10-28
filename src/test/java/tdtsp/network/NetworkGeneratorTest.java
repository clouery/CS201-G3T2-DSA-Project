package tdtsp.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void haversineMatchesKnownDistance() {
        City kualaLumpur = new City(1, "Kuala Lumpur", 3.1412, 101.68653, "P", "PPLC", 1, "14");
        City johorBahru = new City(2, "Johor Bahru", 1.4655, 103.7578, "P", "PPLA", 1, "01");
        assertEquals(296.1, NetworkGenerator.haversineKm(kualaLumpur, johorBahru), 5.0);
    }

    @Test
    void generatesAValidDeterministicNetwork() throws Exception {
        Path processed1 = tempDir.resolve("processed-1");
        Path gtfs1 = tempDir.resolve("gtfs-1");
        Path processed2 = tempDir.resolve("processed-2");
        Path gtfs2 = tempDir.resolve("gtfs-2");

        NetworkGenerator generator = new NetworkGenerator(Path.of("config.properties"));
        generator.generate(Path.of("data/processed/cities_top30.csv"), processed1, gtfs1);
        generator.generate(Path.of("data/processed/cities_top30.csv"), processed2, gtfs2);

        for (String file : List.of("city_distances.csv", "network_edges.csv", "lines.csv", "network_report.md")) {
            assertArrayEquals(Files.readAllBytes(processed1.resolve(file)),
                    Files.readAllBytes(processed2.resolve(file)), file);
        }
        for (String file : List.of("stops.csv", "routes.csv", "trips.csv", "stop_times.csv", "transfers.csv")) {
            assertArrayEquals(Files.readAllBytes(gtfs1.resolve(file)),
                    Files.readAllBytes(gtfs2.resolve(file)), file);
        }

        List<String[]> stops = Csv.read(gtfs1.resolve("stops.csv"));
        assertEquals(31, stops.size());
        Set<String> stopIds = new HashSet<>();
        for (int i = 1; i < stops.size(); i++) {
            stopIds.add(stops.get(i)[0]);
        }
        assertEquals(30, stopIds.size());

        List<String[]> routes = Csv.read(gtfs1.resolve("routes.csv"));
        assertTrue(routes.size() >= 11 && routes.size() <= 13, "routes: " + routes.size());
        Map<String, String> routeModes = new HashMap<>();
        for (int i = 1; i < routes.size(); i++) {
            routeModes.put(routes.get(i)[0], routes.get(i)[1]);
        }

        List<String[]> lineRows = Csv.read(processed1.resolve("lines.csv"));
        Map<String, List<String>> lineStops = new LinkedHashMap<>();
        for (int i = 1; i < lineRows.size(); i++) {
            lineStops.computeIfAbsent(lineRows.get(i)[0], key -> new ArrayList<>()).add(lineRows.get(i)[5]);
        }
        assertTrue(lineStops.size() >= 10 && lineStops.size() <= 12,
                "lines: " + lineStops.size());
        Set<String> covered = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : lineStops.entrySet()) {
            assertTrue(entry.getValue().size() >= 4 && entry.getValue().size() <= 8, entry.getKey());
            covered.addAll(entry.getValue());
        }
        assertEquals(stopIds, covered);

        Set<String> graphEdges = new HashSet<>();
        List<String[]> edgeRows = Csv.read(processed1.resolve("network_edges.csv"));
        for (int i = 1; i < edgeRows.size(); i++) {
            graphEdges.add(edgeRows.get(i)[0] + "-" + edgeRows.get(i)[1]);
            graphEdges.add(edgeRows.get(i)[1] + "-" + edgeRows.get(i)[0]);
        }
        for (List<String> stopsOnLine : lineStops.values()) {
            for (int i = 1; i < stopsOnLine.size(); i++) {
                assertTrue(graphEdges.contains(stopsOnLine.get(i - 1) + "-" + stopsOnLine.get(i)),
                        "line segment is not a graph edge: " + stopsOnLine.get(i - 1)
                                + " -> " + stopsOnLine.get(i));
            }
        }

        // The service lines must form one connected network, not just cover every city.
        Map<String, Set<String>> lineAdjacency = new HashMap<>();
        for (List<String> stopsOnLine : lineStops.values()) {
            for (int i = 1; i < stopsOnLine.size(); i++) {
                String a = stopsOnLine.get(i - 1);
                String b = stopsOnLine.get(i);
                lineAdjacency.computeIfAbsent(a, key -> new HashSet<>()).add(b);
                lineAdjacency.computeIfAbsent(b, key -> new HashSet<>()).add(a);
            }
        }
        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String firstStop = stopIds.iterator().next();
        reached.add(firstStop);
        queue.add(firstStop);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (String next : lineAdjacency.getOrDefault(current, Set.of())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        assertEquals(stopIds, reached, "service line network is disconnected");

        List<String[]> tripRows = Csv.read(gtfs1.resolve("trips.csv"));
        assertTrue(tripRows.size() > 1);
        Map<String, Integer> tripStart = new HashMap<>();
        Map<String, String> tripRoute = new HashMap<>();
        Map<String, Integer> tripDirection = new HashMap<>();
        for (int i = 1; i < tripRows.size(); i++) {
            tripStart.put(tripRows.get(i)[0], Integer.parseInt(tripRows.get(i)[3]));
            tripRoute.put(tripRows.get(i)[0], tripRows.get(i)[1]);
            tripDirection.put(tripRows.get(i)[0], Integer.parseInt(tripRows.get(i)[2]));
        }

        List<String[]> stopTimeRows = Csv.read(gtfs1.resolve("stop_times.csv"));
        Map<String, List<int[]>> byTrip = new LinkedHashMap<>();
        for (int i = 1; i < stopTimeRows.size(); i++) {
            String tripId = stopTimeRows.get(i)[0];
            byTrip.computeIfAbsent(tripId, key -> new ArrayList<>()).add(new int[]{
                    Integer.parseInt(stopTimeRows.get(i)[1]),
                    Integer.parseInt(stopTimeRows.get(i)[3]),
                    Integer.parseInt(stopTimeRows.get(i)[4])});
        }
        assertEquals(tripStart.size(), byTrip.size());
        for (Map.Entry<String, List<int[]>> entry : byTrip.entrySet()) {
            List<int[]> times = entry.getValue();
            times.sort(Comparator.comparingInt(value -> value[0]));
            assertEquals(1, times.get(0)[0]);
            assertEquals(tripStart.get(entry.getKey()).intValue(), times.get(0)[1]);
            for (int i = 1; i < times.size(); i++) {
                assertTrue(times.get(i)[1] > times.get(i - 1)[1], "non-monotonic trip " + entry.getKey());
            }
        }

        Map<String, List<Integer>> byRouteDirection = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tripStart.entrySet()) {
            String key = tripRoute.get(entry.getKey()) + "-" + tripDirection.get(entry.getKey());
            byRouteDirection.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry.getValue());
        }
        for (Map.Entry<String, List<Integer>> entry : byRouteDirection.entrySet()) {
            List<Integer> departures = entry.getValue();
            Collections.sort(departures);
            String route = entry.getKey().substring(0, entry.getKey().indexOf('-'));
            int headway = "bus".equals(routeModes.get(route)) ? 30 : 60;
            assertEquals(360, departures.get(0));
            assertEquals(1320, departures.get(departures.size() - 1));
            for (int i = 1; i < departures.size(); i++) {
                assertEquals(headway, departures.get(i) - departures.get(i - 1));
            }
        }

        List<String[]> transfers = Csv.read(gtfs1.resolve("transfers.csv"));
        assertEquals(31, transfers.size());
        for (int i = 1; i < transfers.size(); i++) {
            assertEquals(transfers.get(i)[0], transfers.get(i)[1]);
            assertEquals("5", transfers.get(i)[2]);
        }
    }
}
