package tdtsp.routing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RoutersTest {

    private static final int BUFFER = 5;

    /*
     * Toy network (src/test/resources/toy):
     *   R1: A -> B -> C every 30 min from 08:00, 30 min per hop.
     *   R2: B -> D at 08:33 and 08:35, 15 min.
     * From A at 08:00: B 08:30, C 09:00 (ride through B, no buffer),
     * D 08:50 (transfer at B: 08:33 is inside the buffer, 08:35 is not).
     */
    private static TransitNetwork toy;
    private static List<Router> toyRouters;

    @BeforeAll
    static void loadToy() throws Exception {
        toy = TransitNetwork.load(Path.of("src/test/resources/toy"), 1);
        toyRouters = routers(toy, BUFFER);
    }

    private static List<Router> routers(TransitNetwork net, int buffer) {
        return List.of(
                new TimeDependentDijkstra(net.buildAdjacency(), buffer),
                new ConnectionScan(net.connections(), net.numStops(), buffer),
                new TimeExpandedGraph(net.connections(), net.numStops(), buffer));
    }

    @Test
    void nextDepartureBinarySearch() {
        int[] deps = {480, 510, 540};
        assertEquals(480, Timetable.nextDeparture(deps, 0));
        assertEquals(510, Timetable.nextDeparture(deps, 481));
        assertEquals(540, Timetable.nextDeparture(deps, 540));
        assertEquals(Timetable.NONE, Timetable.nextDeparture(deps, 541));
        assertEquals(Timetable.NONE, Timetable.nextDeparture(new int[0], 0));
    }

    @Test
    void toyEarliestArrivalsMatchHandComputedAnswers() {
        int[] expected = {480, 510, 540, 530};
        for (Router r : toyRouters) {
            for (int v = 0; v < 4; v++) {
                assertEquals(expected[v], r.earliestArrival(0, v, 480), r.getClass().getSimpleName() + " stop " + v);
            }
        }
    }

    @Test
    void toyLegBreakdown() {
        for (Router r : toyRouters) {
            String name = r.getClass().getSimpleName();
            Leg throughB = r.leg(0, 2, 480);
            assertEquals(new Leg(0, 2, 480, 540, 60, 0, 0), throughB, name);
            Leg transfer = r.leg(0, 3, 480);
            assertEquals(new Leg(0, 3, 480, 530, 45, 5, 1), transfer, name);
            Leg waitAtOrigin = r.leg(0, 1, 481);
            assertEquals(new Leg(0, 1, 481, 540, 30, 29, 0), waitAtOrigin, name);
        }
    }

    @Test
    void missedConnectionByOneMinute() {
        // Ready at B at 08:31 with a buffer only applies after arriving by vehicle; starting
        // at B itself has no buffer, so 08:33 is still catchable at 08:33 but not at 08:34.
        for (Router r : toyRouters) {
            assertEquals(528, r.earliestArrival(1, 3, 513), r.getClass().getSimpleName());
            assertEquals(530, r.earliestArrival(1, 3, 514), r.getClass().getSimpleName());
            assertEquals(Router.INF, r.earliestArrival(1, 3, 516), r.getClass().getSimpleName());
        }
    }

    @Test
    void edgeCases() {
        for (Router r : toyRouters) {
            String name = r.getClass().getSimpleName();
            assertEquals(new Leg(0, 0, 480, 480, 0, 0, 0), r.leg(0, 0, 480), name);
            assertFalse(r.leg(2, 0, 480).feasible(), name + ": no route back from C");
            assertFalse(r.leg(0, 2, 600).feasible(), name + ": after the last service");
            assertEquals(Router.INF, r.earliestArrival(0, 2, Router.INF), name);
        }
    }

    @Test
    void zeroBufferAllowsTheTightTransfer() {
        for (Router r : routers(toy, 0)) {
            assertEquals(528, r.earliestArrival(0, 3, 480), r.getClass().getSimpleName());
        }
    }

    @Test
    void approachesAgreeOnTheGeneratedTimetable() throws Exception {
        TransitNetwork net = TransitNetwork.load(Path.of("data/gtfs"), 3);
        List<Router> all = routers(net, BUFFER);
        TimeDependentDijkstra dijkstra = (TimeDependentDijkstra) all.get(0);
        ConnectionScan csa = (ConnectionScan) all.get(1);
        TimeExpandedGraph te = (TimeExpandedGraph) all.get(2);

        Random random = new Random(7);
        for (int q = 0; q < 40; q++) {
            int s = random.nextInt(net.numStops());
            int t0 = 360 + random.nextInt(960);
            int[] expected = csa.earliestArrival(s, t0);
            assertArrayEquals(expected, dijkstra.earliestArrival(s, t0), "dijkstra from " + s);
            assertArrayEquals(expected, te.earliestArrival(s, t0), "time-expanded from " + s);

            int v = random.nextInt(net.numStops());
            Leg reference = csa.leg(s, v, t0);
            for (Router r : all) {
                Leg leg = r.leg(s, v, t0);
                assertEquals(reference.arrival(), leg.arrival(), r.getClass().getSimpleName());
                if (leg.feasible()) {
                    assertEquals(leg.arrival() - t0, leg.waiting() + leg.ride());
                    assertTrue(leg.waiting() >= 0 && leg.ride() >= 0 && leg.transfers() >= 0);
                }
            }
        }
    }

    @Test
    void secondaryHeuristicsNeverBeatEarliestArrival() throws Exception {
        TransitNetwork net = TransitNetwork.load(Path.of("data/gtfs"), 3);
        List<List<Edge>> adj = net.buildAdjacency();
        Router arrival = new TimeDependentDijkstra(adj, BUFFER);
        Router minWait = new TimeDependentDijkstra(adj, BUFFER, TimeDependentDijkstra.Objective.WAIT);
        Router minRide = new TimeDependentDijkstra(adj, BUFFER, TimeDependentDijkstra.Objective.RIDE);

        Random random = new Random(11);
        for (int q = 0; q < 60; q++) {
            int s = random.nextInt(net.numStops());
            int v = random.nextInt(net.numStops());
            int t0 = 360 + random.nextInt(960);
            Leg best = arrival.leg(s, v, t0);
            for (Router h : List.of(minWait, minRide)) {
                Leg leg = h.leg(s, v, t0);
                if (leg.feasible()) {
                    assertTrue(leg.arrival() >= best.arrival());
                    assertEquals(leg.arrival() - t0, leg.waiting() + leg.ride());
                }
            }
            Leg rideLeg = minRide.leg(s, v, t0);
            if (rideLeg.feasible() && best.feasible()) {
                assertTrue(rideLeg.ride() <= best.ride(), "min-ride rides no longer than min-arrival");
            }
        }
    }
}
