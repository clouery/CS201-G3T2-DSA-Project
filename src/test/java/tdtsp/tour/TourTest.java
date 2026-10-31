package tdtsp.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import tdtsp.routing.ConnectionScan;
import tdtsp.routing.MemoRouter;
import tdtsp.routing.Router;
import tdtsp.routing.TransitNetwork;

class TourTest {

    @Test
    void toyTourCountsTheDeadEndAsInfeasible() throws Exception {
        TransitNetwork toy = TransitNetwork.load(Path.of("src/test/resources/toy"), 1);
        Router csa = new ConnectionScan(toy.connections(), toy.numStops(), 5);
        List<Integer> tour = NearestNeighbourTour.build(List.of(0, 1, 2, 3), 0, 480, csa);
        assertEquals(List.of(0, 1, 3, 2), tour);

        TourResult r = TourEvaluator.evaluate(tour, 480, csa);
        assertEquals(1, r.infeasibleLegs(), "nothing leaves D");
        assertEquals(528, r.arrival(), "the infeasible last leg is skipped");
        assertEquals(TourResult.PENALTY + 48, r.cost());
    }

    @Test
    void localSearchNeverWorsensAndKeepsAPermutation() throws Exception {
        TransitNetwork net = TransitNetwork.load(Path.of("data/gtfs"), 14);
        Router router = new MemoRouter(new ConnectionScan(net.connections(), net.numStops(), 5));
        List<Integer> cities = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cities.add(i);
        }
        List<Integer> nn = NearestNeighbourTour.build(cities, 0, 480, router);
        TourResult before = TourEvaluator.evaluate(nn, 480, router);
        TourResult after = LocalSearch.improve(nn, 480, router, 3);

        assertTrue(after.cost() <= before.cost());
        assertEquals(0, after.tour().get(0));
        assertEquals(new HashSet<>(cities), new HashSet<>(after.tour()));
        assertEquals(cities.size(), after.tour().size());
        assertEquals(after.elapsed(), after.waiting() + after.ride(), "no infeasible legs on this instance");
    }

    @Test
    void classicTourIsAPermutationAndTwoOptHelps() {
        double[][] d = {
                {0, 1, 5, 2},
                {1, 0, 2, 5},
                {5, 2, 0, 1},
                {2, 5, 1, 0},
        };
        List<Integer> tour = ClassicTour.build(d, 0);
        assertEquals(0, tour.get(0));
        assertEquals(new HashSet<>(List.of(0, 1, 2, 3)), new HashSet<>(tour));
        assertEquals(4.0, ClassicTour.lengthKm(tour, d), 1e-9);
    }
}
