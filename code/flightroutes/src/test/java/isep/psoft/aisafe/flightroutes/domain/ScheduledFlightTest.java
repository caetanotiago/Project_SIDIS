package isep.psoft.aisafe.flightroutes.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledFlightTest {

    private static final RouteSummary REMOTE_ROUTE =
            new RouteSummary("route-on-another-replica", "opo", "lis", 274.0, 400.0, 120, 55, "ACTIVE");

    @Test
    void ensureNewFlightIsScheduledAndKeepsARouteSnapshot() {
        ScheduledFlight flight = new ScheduledFlight("CS-TUA", RouteReference.of(REMOTE_ROUTE),
                new FlightSchedule(LocalDate.of(2026, 11, 2), LocalTime.of(8, 0)));

        assertEquals("SCHEDULED", flight.getStatus().getState());
        assertEquals("route-on-another-replica", flight.getRoute().getRouteId());
        assertEquals("OPO", flight.getRoute().getOriginIata());
        assertEquals(55, flight.getRoute().getEstimatedFlightTimeMinutes());
    }

    @Test
    void ensureMandatoryData() {
        FlightSchedule schedule = new FlightSchedule(LocalDate.of(2026, 11, 2), LocalTime.of(8, 0));
        RouteReference route = RouteReference.of(REMOTE_ROUTE);

        assertThrows(IllegalArgumentException.class, () -> new ScheduledFlight(" ", route, schedule));
        assertThrows(IllegalArgumentException.class, () -> new ScheduledFlight("CS-TUA", null, schedule));
        assertThrows(IllegalArgumentException.class, () -> new ScheduledFlight("CS-TUA", route, null));
        assertThrows(IllegalArgumentException.class, () -> new FlightSchedule(null, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> new RouteReference("", "OPO", "LIS", 55));
        assertThrows(IllegalArgumentException.class, () -> new RouteReference("r1", "OPO", "LIS", 0));
    }
}
