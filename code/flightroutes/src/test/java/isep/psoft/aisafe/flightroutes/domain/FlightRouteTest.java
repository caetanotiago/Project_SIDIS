package isep.psoft.aisafe.flightroutes.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlightRouteTest {

    private static FlightRoute route() {
        return new FlightRoute("opo", "lis", new RouteDistance(274.0),
                new RouteRequirements(400.0, 120), new EstimatedFlightTime(55));
    }

    @Test
    void ensureRouteRequirementsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RouteRequirements(-100.0, 150));
        assertThrows(IllegalArgumentException.class, () -> new RouteRequirements(1000.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EstimatedFlightTime(0));
        assertThrows(IllegalArgumentException.class, () -> new RouteDistance(-1.0));
    }

    @Test
    void ensureNewRouteIsActiveAndCreationIsRecorded() {
        FlightRoute route = route();

        assertEquals("ACTIVE", route.getStatus().getState());
        assertEquals("OPO", route.getOriginIata());
        assertEquals(1, route.getHistoryLog().size());
        assertEquals("Route created.", route.getHistoryLog().get(0).getDescription());
        assertEquals(120, route.getHistoryLog().get(0).getNewMinCapacity());
    }

    @Test
    void ensureHistoryOnlyRecordsChangedAttributes() {
        FlightRoute route = route();

        route.updateDetails(null, 150, 55); // só a capacidade muda

        RouteHistory entry = route.getHistoryLog().get(1);
        assertEquals(120, entry.getPreviousMinCapacity());
        assertEquals(150, entry.getNewMinCapacity());
        assertNull(entry.getPreviousEstimatedFlightTime());
        assertNull(entry.getPreviousMinRange());
    }

    @Test
    void ensureNoHistoryWhenNothingChanges() {
        FlightRoute route = route();

        route.updateDetails(400.0, 120, 55);

        assertEquals(1, route.getHistoryLog().size());
    }

    @Test
    void ensureStatusTransitions() {
        FlightRoute route = route();

        route.changeStatus("INACTIVE");
        assertEquals("INACTIVE", route.getStatus().getState());
        assertThrows(IllegalStateException.class, () -> route.changeStatus("INACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> route.changeStatus("CLOSED"));

        route.changeStatus("ACTIVE");
        assertEquals(3, route.getHistoryLog().size());
    }

    @Test
    void ensureRequirementsCheckRangeAndCapacity() {
        RouteRequirements req = new RouteRequirements(1000.0, 150);

        assertTrue(req.isMetBy(5000.0, 180));
        assertFalse(req.isMetBy(500.0, 180));  // alcance insuficiente
        assertFalse(req.isMetBy(5000.0, 100)); // capacidade insuficiente
    }

    @Test
    void ensureSummaryReflectsTheRoute() {
        RouteSummary summary = route().toSummary();

        assertEquals("OPO", summary.originIata());
        assertEquals(274.0, summary.distance());
        assertTrue(summary.isActive());
        assertTrue(summary.isMetBy(500.0, 150));
    }
}
