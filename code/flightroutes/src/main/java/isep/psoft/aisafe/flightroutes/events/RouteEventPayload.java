package isep.psoft.aisafe.flightroutes.events;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;

/**
 * Payload of {@code RouteCreated}, {@code RouteUpdated} and {@code RouteDeleted}: the full state of the route
 * after the change (a "state-carrying" event, so a consumer never has to call Flight Routes back).
 *
 * <pre>
 * { "routeId": "8c1d...", "originIata": "OPO", "destinationIata": "LIS", "distance": 274.0,
 *   "minRange": 400.0, "minCapacity": 120, "estimatedFlightTime": 55, "status": "ACTIVE", "version": 0 }
 * </pre>
 *
 * @param version the route's optimistic-lock version on the owner replica: it grows with every update of the
 *                route, so a consumer can ignore an event older than what it already has. For
 *                {@code RouteDeleted} only {@code routeId} and {@code version} are filled in.
 */
public record RouteEventPayload(
        String routeId,
        String originIata,
        String destinationIata,
        Double distance,
        Double minRange,
        Integer minCapacity,
        Integer estimatedFlightTime,
        String status,
        Long version) {

    public static RouteEventPayload of(RouteSummary route, Long version) {
        return new RouteEventPayload(route.id(), route.originIata(), route.destinationIata(), route.distance(),
                route.minRange(), route.minCapacity(), route.estimatedFlightTime(), route.status(), version);
    }

    public static RouteEventPayload deleted(String routeId, Long version) {
        return new RouteEventPayload(routeId, null, null, null, null, null, null, null, version);
    }
}
