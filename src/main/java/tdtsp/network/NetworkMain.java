package tdtsp.network;

import java.nio.file.Path;

/**
 * CLI entry point for network and timetable generation.
 *
 * <pre>
 * java -cp target/classes tdtsp.network.NetworkMain \
 *   data/processed/cities_top30.csv config.properties data/processed data/gtfs
 * </pre>
 */
public final class NetworkMain {

    private NetworkMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            System.err.println("usage: NetworkMain [cities.csv] [config.properties] [processedDir] [gtfsDir]");
            System.exit(2);
        }
        Path cities = Path.of(args.length > 0 ? args[0] : "data/processed/cities_top30.csv");
        Path config = Path.of(args.length > 1 ? args[1] : "config.properties");
        Path processed = Path.of(args.length > 2 ? args[2] : "data/processed");
        Path gtfs = Path.of(args.length > 3 ? args[3] : "data/gtfs");

        new NetworkGenerator(config).generate(cities, processed, gtfs);
        System.out.printf("Generated network for %s into %s and %s%n", cities, processed, gtfs);
    }
}
