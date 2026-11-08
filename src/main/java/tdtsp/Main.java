
package tdtsp;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import tdtsp.cli.RoutePlannerCLI;
import tdtsp.routing.TransitNetwork;

public class Main {

    public static void main(String[] args) {

        Path gtfsDir = Path.of("data", "gtfs");
        Path configPath = Path.of("config.properties");

        try {
            Properties config = new Properties();

            try (Reader reader = Files.newBufferedReader(
                    configPath, StandardCharsets.UTF_8)) {
                config.load(reader);
            }

            int transferTimeBuffer = Integer.parseInt(
                    config.getProperty("transfer.bufferMin", "5"));

            TransitNetwork network = TransitNetwork.load(gtfsDir, 1);

            System.out.println("Loaded " + network.numStops() + " stops.");
            System.out.println("Loaded " + network.numTrips() + " trips.");

            RoutePlannerCLI cli =
                    new RoutePlannerCLI(network, gtfsDir, transferTimeBuffer);

            cli.run();

        } catch (IOException e) {
            System.err.println("Unable to load the timetable: "
                    + e.getMessage());
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
