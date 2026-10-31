package tdtsp.routing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tdtsp.network.Csv;

/**
 * Timetable loaded from the GTFS-like CSVs in {@code data/gtfs/} (plan §4.6).
 *
 * <p>The generated timetable covers one service day. A tour of 30 cities takes
 * several days, so the day is repeated {@code days} times: day {@code d} is the
 * same timetable shifted by {@code d * 1440} minutes, with its own trip ids.
 */
public final class TransitNetwork {

    public static final int DAY_MIN = 1440;

    private final String[] stopIds;
    private final String[] names;
    private final double[] lat;
    private final double[] lon;
    private final Map<String, Integer> nameIndex;
    private final String[] tripPattern;
    private final int[][] tripStops;
    private final int[][] tripArrivals;
    private final int[][] tripDepartures;
    private final Connection[] connections;

    private TransitNetwork(String[] stopIds, String[] names, double[] lat, double[] lon,
                           String[] tripPattern, int[][] tripStops, int[][] tripArrivals, int[][] tripDepartures) {
        this.stopIds = stopIds;
        this.names = names;
        this.lat = lat;
        this.lon = lon;
        this.nameIndex = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            nameIndex.putIfAbsent(names[i], i);
        }
        this.tripPattern = tripPattern;
        this.tripStops = tripStops;
        this.tripArrivals = tripArrivals;
        this.tripDepartures = tripDepartures;
        this.connections = buildConnections();
    }

    /** Loads {@code stops.csv}, {@code trips.csv} and {@code stop_times.csv}, repeating the day {@code days} times. */
    public static TransitNetwork load(Path gtfsDir, int days) throws IOException {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1");
        }
        List<String[]> stops = Csv.read(gtfsDir.resolve("stops.csv"));
        int n = stops.size() - 1;
        String[] stopIds = new String[n];
        String[] names = new String[n];
        double[] lat = new double[n];
        double[] lon = new double[n];
        Map<String, Integer> stopIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String[] row = stops.get(i + 1);
            stopIds[i] = row[0];
            names[i] = row[1];
            lat[i] = Double.parseDouble(row[2]);
            lon[i] = Double.parseDouble(row[3]);
            stopIndex.put(row[0], i);
        }

        // trip_id -> "route|direction", the key that identifies one line in one direction.
        Map<String, String> patternOf = new LinkedHashMap<>();
        List<String[]> trips = Csv.read(gtfsDir.resolve("trips.csv"));
        for (int i = 1; i < trips.size(); i++) {
            String[] row = trips.get(i);
            patternOf.put(row[0], row[1] + "|" + row[2]);
        }

        // stop_times rows grouped by trip, ordered by stop_sequence.
        Map<String, List<int[]>> times = new LinkedHashMap<>();
        for (String tripId : patternOf.keySet()) {
            times.put(tripId, new ArrayList<>());
        }
        List<String[]> stopTimes = Csv.read(gtfsDir.resolve("stop_times.csv"));
        for (int i = 1; i < stopTimes.size(); i++) {
            String[] row = stopTimes.get(i);
            List<int[]> list = times.get(row[0]);
            Integer stop = stopIndex.get(row[2]);
            if (list == null || stop == null) {
                throw new IllegalArgumentException("stop_times row " + i + " references an unknown trip or stop");
            }
            list.add(new int[]{Integer.parseInt(row[1]), stop,
                    Integer.parseInt(row[3]), Integer.parseInt(row[4])});
        }

        int perDay = times.size();
        int total = perDay * days;
        String[] tripPattern = new String[total];
        int[][] tripStops = new int[total][];
        int[][] tripArrivals = new int[total][];
        int[][] tripDepartures = new int[total][];
        int t = 0;
        for (Map.Entry<String, List<int[]>> entry : times.entrySet()) {
            List<int[]> rows = entry.getValue();
            rows.sort((a, b) -> Integer.compare(a[0], b[0]));
            for (int d = 0; d < days; d++) {
                int id = d * perDay + t;
                int offset = d * DAY_MIN;
                tripPattern[id] = patternOf.get(entry.getKey());
                tripStops[id] = new int[rows.size()];
                tripArrivals[id] = new int[rows.size()];
                tripDepartures[id] = new int[rows.size()];
                for (int k = 0; k < rows.size(); k++) {
                    tripStops[id][k] = rows.get(k)[1];
                    tripArrivals[id][k] = rows.get(k)[2] + offset;
                    tripDepartures[id][k] = rows.get(k)[3] + offset;
                }
            }
            t++;
        }
        return new TransitNetwork(stopIds, names, lat, lon, tripPattern, tripStops, tripArrivals, tripDepartures);
    }

    private Connection[] buildConnections() {
        List<Connection> list = new ArrayList<>();
        for (int trip = 0; trip < tripStops.length; trip++) {
            for (int k = 0; k + 1 < tripStops[trip].length; k++) {
                list.add(new Connection(tripDepartures[trip][k], tripStops[trip][k],
                        tripArrivals[trip][k + 1], tripStops[trip][k + 1], trip));
            }
        }
        return list.toArray(Connection[]::new);
    }

    /**
     * Builds the time-dependent adjacency list: one {@link Edge} per line segment and
     * direction, holding that segment's sorted departures (plan §5.1).
     */
    public List<List<Edge>> buildAdjacency() {
        // "pattern|from|to" -> {departure, trip, ride} rows
        Map<String, List<int[]>> segments = new LinkedHashMap<>();
        for (int trip = 0; trip < tripStops.length; trip++) {
            for (int k = 0; k + 1 < tripStops[trip].length; k++) {
                String key = tripPattern[trip] + "|" + tripStops[trip][k] + "|" + tripStops[trip][k + 1];
                int dep = tripDepartures[trip][k];
                segments.computeIfAbsent(key, x -> new ArrayList<>())
                        .add(new int[]{dep, trip, tripArrivals[trip][k + 1] - dep});
            }
        }
        List<List<Edge>> adj = new ArrayList<>();
        for (int i = 0; i < numStops(); i++) {
            adj.add(new ArrayList<>());
        }
        for (Map.Entry<String, List<int[]>> entry : segments.entrySet()) {
            List<int[]> rows = entry.getValue();
            rows.sort((a, b) -> Integer.compare(a[0], b[0]));
            int ride = rows.get(0)[2];
            int[] departures = new int[rows.size()];
            int[] tripIds = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i)[2] != ride) {
                    throw new IllegalStateException("segment " + entry.getKey() + " has varying ride times");
                }
                departures[i] = rows.get(i)[0];
                tripIds[i] = rows.get(i)[1];
            }
            String[] parts = entry.getKey().split("\\|");
            int from = Integer.parseInt(parts[2]);
            int to = Integer.parseInt(parts[3]);
            adj.get(from).add(new Edge(to, departures, tripIds, ride));
        }
        return adj;
    }

    /** All connections in trip order (unsorted); callers sort as part of their preprocessing. */
    public Connection[] connections() {
        return connections.clone();
    }

    public int numStops() {
        return stopIds.length;
    }

    public int numTrips() {
        return tripStops.length;
    }

    public String stopId(int stop) {
        return stopIds[stop];
    }

    public String name(int stop) {
        return names[stop];
    }

    public double lat(int stop) {
        return lat[stop];
    }

    public double lon(int stop) {
        return lon[stop];
    }

    /** Stop id for a city name, or -1. */
    public int indexOf(String name) {
        return nameIndex.getOrDefault(name, -1);
    }
}
