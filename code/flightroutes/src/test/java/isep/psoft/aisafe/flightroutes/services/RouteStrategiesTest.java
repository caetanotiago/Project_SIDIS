package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.services.strategy.DistanceSortStrategy;
import isep.psoft.aisafe.flightroutes.services.strategy.MinStopsRouteSearch;
import isep.psoft.aisafe.flightroutes.services.strategy.PopularitySortStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteStrategiesTest {

    private static RouteSummary route(String id, String from, String to, double km) {
        return new RouteSummary(id, from, to, km, 100.0, 50, 60, "ACTIVE");
    }

    // Rede espalhada por réplicas diferentes (o algoritmo só vê a vista agregada)
    private final List<RouteSummary> network = List.of(
            route("1", "OPO", "LIS", 274),
            route("2", "LIS", "MAD", 503),
            route("3", "MAD", "CDG", 1053),
            route("4", "OPO", "MAD", 423),
            route("5", "CDG", "OPO", 1200)); // ciclo de volta à origem

    @Test
    void minStopsSearchReturnsOnlyTheItinerariesWithFewestStops() {
        List<Itinerary> result = new MinStopsRouteSearch().search(network, "OPO", "CDG");

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getNumberOfStops());
        assertEquals(List.of("4", "3"), result.get(0).getLegs().stream().map(RouteSummary::id).toList());
        assertEquals(1476.0, result.get(0).getTotalDistance());
    }

    @Test
    void minStopsSearchReturnsAllItinerariesOfTheSameLength() {
        List<Itinerary> result = new MinStopsRouteSearch().search(network, "LIS", "CDG");

        assertEquals(1, result.size());
        assertEquals(List.of("2", "3"), result.get(0).getLegs().stream().map(RouteSummary::id).toList());
    }

    @Test
    void minStopsSearchIsEmptyWhenUnreachable() {
        assertTrue(new MinStopsRouteSearch().search(network, "LIS", "FNC").isEmpty());
    }

    @Test
    void sortStrategies() {
        List<RouteUsage> usages = List.of(
                new RouteUsage(route("a", "OPO", "LIS", 274), 5),
                new RouteUsage(route("b", "MAD", "CDG", 1053), 1),
                new RouteUsage(route("c", "LIS", "MAD", 503), 9));

        assertEquals(List.of("c", "a", "b"),
                new PopularitySortStrategy().sort(usages).stream().map(u -> u.route().id()).toList());
        assertEquals(List.of("b", "c", "a"),
                new DistanceSortStrategy().sort(usages).stream().map(u -> u.route().id()).toList());
    }
}
