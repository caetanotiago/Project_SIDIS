package isep.psoft.aisafe.flightroutes.domain;

import lombok.Getter;

/**
 * Read projection (US214): pairs an active FlightRoute with its usage count
 * (number of ScheduledFlight created on it = route popularity).
 * Populated via a JPQL constructor expression in FlightRouteRepository.
 */
@Getter
public class RouteUsage {

    private final FlightRoute route;
    private final long usageCount;

    public RouteUsage(FlightRoute route, Long usageCount) {
        this.route = route;
        this.usageCount = usageCount == null ? 0L : usageCount;
    }
}
