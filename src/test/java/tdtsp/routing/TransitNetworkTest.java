
package tdtsp.routing;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TransitNetworkTest {

    @Test
    void loadsTripEventsCorrectly() throws Exception {
        TransitNetwork network =
                TransitNetwork.load(Path.of("src/test/resources/toy"), 1);

        // Trip 0 is A1: Alpha -> Bravo -> Charlie.
        int trip = 0;

        assertEquals(3, network.tripStopCount(trip));

        // Stop indexes follow the order in stops.csv.
        assertEquals(0, network.tripStop(trip, 0)); // Alpha
        assertEquals(1, network.tripStop(trip, 1)); // Bravo
        assertEquals(2, network.tripStop(trip, 2)); // Charlie

        // Arrival and departure times are preserved separately.
        assertEquals(480, network.tripArrival(trip, 0));
        assertEquals(480, network.tripDeparture(trip, 0));

        assertEquals(510, network.tripArrival(trip, 1));
        assertEquals(510, network.tripDeparture(trip, 1));

        assertEquals(540, network.tripArrival(trip, 2));
        assertEquals(540, network.tripDeparture(trip, 2));

        assertEquals("R1|0", network.tripPattern(trip));
    }

    @Test
    void repeatsTripEventsForMultipleDays() throws Exception {
        TransitNetwork network =
                TransitNetwork.load(Path.of("src/test/resources/toy"), 2);

        int tripsPerDay = 6; // A1-A4 and B1-B2
        int firstDayTrip = 0;
        int secondDayTrip = tripsPerDay;

        assertEquals(network.tripStopCount(firstDayTrip),
                     network.tripStopCount(secondDayTrip));

        for (int i = 0; i < network.tripStopCount(firstDayTrip); i++) {
            assertEquals(
                    network.tripStop(firstDayTrip, i),
                    network.tripStop(secondDayTrip, i)
            );

            assertEquals(
                    network.tripArrival(firstDayTrip, i) + TransitNetwork.DAY_MIN,
                    network.tripArrival(secondDayTrip, i)
            );

            assertEquals(
                    network.tripDeparture(firstDayTrip, i) + TransitNetwork.DAY_MIN,
                    network.tripDeparture(secondDayTrip, i)
            );
        }

        assertEquals(
                network.tripPattern(firstDayTrip),
                network.tripPattern(secondDayTrip)
        );

        assertTrue(network.tripArrival(secondDayTrip, 0) >= TransitNetwork.DAY_MIN);
    }
}
