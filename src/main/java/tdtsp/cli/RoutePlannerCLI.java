
package tdtsp.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import tdtsp.network.Csv;
import tdtsp.routing.ConnectionScan;
import tdtsp.routing.Leg;
import tdtsp.routing.Router;
import tdtsp.routing.TimeDependentDijkstra;
import tdtsp.routing.TimeExpandedGraph;
import tdtsp.routing.TransitNetwork;

public class RoutePlannerCLI {

    private static final int PAGE_SIZE = 10;

    private final TransitNetwork network;
    private final Map<String, Router> routers = new HashMap<>();

    private final List<Stop> stops = new ArrayList<>();
    private final List<RouteInfo> routes = new ArrayList<>();
    private final List<TripInfo> trips = new ArrayList<>();
    private final Map<String, List<StopTime>> stopTimes = new HashMap<>();

    public RoutePlannerCLI(TransitNetwork network, Path gtfsDir, int buffer)
            throws IOException {

        this.network = network;

        // Build the three routers once.
        routers.put("tdd",
                new TimeDependentDijkstra(network.buildAdjacency(), buffer));

        routers.put("csa",
                new ConnectionScan(network.connections(), network.numStops(), buffer));

        routers.put("ted",
                new TimeExpandedGraph(network.connections(), network.numStops(), buffer));

        loadData(gtfsDir);
    }

    private void loadData(Path gtfsDir) throws IOException {

        // Stops
        List<String[]> stopRows = Csv.read(gtfsDir.resolve("stops.csv"));

        for (int i = 1; i < stopRows.size(); i++) {
            String[] row = stopRows.get(i);

            stops.add(new Stop(row[0], row[1]));
        }

        stops.sort(Comparator.comparing(
                Stop::name, String.CASE_INSENSITIVE_ORDER));

        // Routes
        List<String[]> routeRows = Csv.read(gtfsDir.resolve("routes.csv"));

        for (int i = 1; i < routeRows.size(); i++) {
            String[] row = routeRows.get(i);

            routes.add(new RouteInfo(row[0], row[1], row[2]));
        }

        routes.sort(Comparator.comparing(
                RouteInfo::name, String.CASE_INSENSITIVE_ORDER));

        // Trips
        List<String[]> tripRows = Csv.read(gtfsDir.resolve("trips.csv"));

        for (int i = 1; i < tripRows.size(); i++) {
            String[] row = tripRows.get(i);

            trips.add(new TripInfo(
                    row[0],
                    row[1],
                    row[2],
                    Integer.parseInt(row[3])
            ));
        }

        trips.sort(Comparator.comparing(TripInfo::id));

        // Stop times, grouped by trip ID.
        List<String[]> timeRows = Csv.read(gtfsDir.resolve("stop_times.csv"));

        for (int i = 1; i < timeRows.size(); i++) {
            String[] row = timeRows.get(i);

            StopTime stopTime = new StopTime(
                    Integer.parseInt(row[1]),
                    row[2],
                    Integer.parseInt(row[3]),
                    Integer.parseInt(row[4])
            );

            stopTimes.computeIfAbsent(row[0], k -> new ArrayList<>())
                    .add(stopTime);
        }

        for (List<StopTime> times : stopTimes.values()) {
            times.sort(Comparator.comparingInt(StopTime::sequence));
        }
    }

    public void run() {

        System.out.println("Transit Route Planner");
        System.out.println("Type 'help' to see available commands.");

        try (Scanner scanner = new Scanner(System.in)) {

            while (true) {
                System.out.print("\n> ");

                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                String[] args = input.split("\\s+");
                String command = args[0].toLowerCase();

                try {
                    switch (command) {
                        case "help" -> showHelp();

                        case "exit", "quit" -> {
                            System.out.println("Goodbye!");
                            return;
                        }

                        case "list" -> handleList(args);

                        case "tdd", "csa", "ted" ->
                                handleRoute(command, args);

                        default -> System.out.println(
                                "Unknown command. Type 'help' for assistance.");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Unable to execute command: "
                            + e.getMessage());
                }
            }
        }
    }

    private void showHelp() {

        System.out.println("""
                
                Available commands
                ----------------------------------------
                help
                    Display this help message.

                exit
                    Exit the application.

                list stops -p <page>
                    List stops alphabetically.

                list stops -trip <trip_id>
                    List stops visited by a particular trip.

                list trips -p <page>
                    List scheduled trips.

                list routes -p <page>
                    List routes.

                tdd <origin> <destination> <HH:mm>
                    Run Time-Dependent Dijkstra.

                csa <origin> <destination> <HH:mm>
                    Run Connection Scan Algorithm.

                ted <origin> <destination> <HH:mm>
                    Run Time-Expanded Dijkstra.

                ----------------------------------------
                Example:
                list stops -p 1
                list stops -trip L01_D0_0360
                ted 1734634 1734705 08:00
                """);
    }

    private void handleList(String[] args) {

        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: list <stops|trips|routes> <option>");
        }

        switch (args[1].toLowerCase()) {

            case "stops" -> {
                if (args[2].equals("-p") && args.length == 4) {
                    showPage(stops, parsePage(args[3]), "Stops");
                } else if (args[2].equals("-trip") && args.length == 4) {
                    showTripStops(args[3]);
                } else {
                    throw new IllegalArgumentException(
                            "Usage: list stops -p <page> OR list stops -trip <trip_id>");
                }
            }

            case "trips" -> {
                if (args[2].equals("-p") && args.length == 4) {
                    showPage(trips, parsePage(args[3]), "Trips");
                } else {
                    throw new IllegalArgumentException(
                            "Usage: list trips -p <page>");
                }
            }

            case "routes" -> {
                if (args[2].equals("-p") && args.length == 4) {
                    showPage(routes, parsePage(args[3]), "Routes");
                } else {
                    throw new IllegalArgumentException(
                            "Usage: list routes -p <page>");
                }
            }

            default -> throw new IllegalArgumentException(
                    "Unknown list category: " + args[1]);
        }
    }

    private int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);

            if (page < 1) {
                throw new IllegalArgumentException(
                        "Page number must be at least 1.");
            }

            return page;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid page number.");
        }
    }

    private <T> void showPage(List<T> items, int page, String title) {

        int totalPages = Math.max(1,
                (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);

        if (page > totalPages) {
            throw new IllegalArgumentException(
                    "Page does not exist. There are " + totalPages + " pages.");
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());

        System.out.println("\n" + title + " (Page "
                + page + " of " + totalPages + ")");

        System.out.println("----------------------------------------");

        for (int i = start; i < end; i++) {
            System.out.println(items.get(i));
        }

        System.out.println("----------------------------------------");
        System.out.printf(
            "Showing entries %d-%d of %d entries.%n",
            start + 1,
            end,
            items.size()
        );
    }

    private void showTripStops(String tripId) {

        List<StopTime> times = stopTimes.get(tripId);

        if (times == null) {
            throw new IllegalArgumentException(
                    "Trip ID not found: " + tripId);
        }

        System.out.println("\nStops visited by trip " + tripId);
        System.out.println("----------------------------------------");

        for (StopTime time : times) {

            int index = findStopIndex(time.stopId());

            if (index < 0) {
                continue;
            }

            System.out.printf(
                    "%2d. %-25s Arrival: %s  Departure: %s%n",
                    time.sequence(),
                    network.name(index),
                    formatTime(time.arrival()),
                    formatTime(time.departure())
            );
        }

        System.out.println("----------------------------------------");
    }

    private int findStopIndex(String stopId) {

        for (int i = 0; i < network.numStops(); i++) {
            if (network.stopId(i).equals(stopId)) {
                return i;
            }
        }

        return -1;
    }

    private void handleRoute(String algorithm, String[] args) {

        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: " + algorithm
                            + " <origin_stop_id> <destination_stop_id> <HH:mm>");
        }

        int from = findStopIndex(args[1]);
        int to = findStopIndex(args[2]);

        if (from < 0) {
            throw new IllegalArgumentException(
                    "Unknown origin stop ID: " + args[1]);
        }

        if (to < 0) {
            throw new IllegalArgumentException(
                    "Unknown destination stop ID: " + args[2]);
        }

        int departure = parseTime(args[3]);

        Router router = routers.get(algorithm);
        Leg leg = router.leg(from, to, departure);

        System.out.println("\n" + algorithm.toUpperCase()
                + " routing result");
        System.out.println("----------------------------------------");

        System.out.println("Origin:      " + network.name(from)
                + " (" + network.stopId(from) + ")");

        System.out.println("Destination: " + network.name(to)
                + " (" + network.stopId(to) + ")");

        System.out.println("Departure:   " + formatTime(departure));

        if (!leg.feasible()) {
            System.out.println("No feasible journey found.");
            System.out.println("----------------------------------------");
            return;
        }

        System.out.println("Arrival:     " + formatTime(leg.arrival()));
        System.out.println("Duration:    " + leg.arrival() + " - "
                + departure + " = " + (leg.arrival() - departure) + " min");
        System.out.println("Ride time:   " + leg.ride() + " min");
        System.out.println("Waiting:     " + leg.waiting() + " min");
        System.out.println("Transfers:   " + leg.transfers());

        System.out.println("----------------------------------------");
    }

    private int parseTime(String value) {

        try {
            String[] parts = value.split(":");

            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            if (hours < 0 || minutes < 0 || minutes >= 60) {
                throw new IllegalArgumentException();
            }

            return hours * 60 + minutes;

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid time. Use HH:mm, for example 08:30.");
        }
    }

    private String formatTime(int time) {

        int hours = time / 60;
        int minutes = time % 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    private record Stop(String id, String name) {
        @Override
        public String toString() {
            return String.format("%-18s %s", id, name);
        }
    }

    private record RouteInfo(String id, String mode, String name) {
        @Override
        public String toString() {
            return String.format("%-10s %-10s %s", id, mode, name);
        }
    }

    private record TripInfo(String id, String routeId,
                            String direction, int startTime) {
        @Override
        public String toString() {
            return String.format("%-20s %-10s %-10s %s",
                    id, routeId, direction, formatStaticTime(startTime));
        }

        private static String formatStaticTime(int time) {
            return String.format("%02d:%02d", time / 60, time % 60);
        }
    }

    private record StopTime(int sequence, String stopId,
                            int arrival, int departure) {
    }
}
