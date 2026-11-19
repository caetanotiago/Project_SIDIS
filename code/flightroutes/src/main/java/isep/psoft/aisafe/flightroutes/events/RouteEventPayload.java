package isep.psoft.aisafe.flightroutes.events;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload of {@code RouteCreated} and {@code RouteUpdated}: the full state of the route after the change, including
 * its change history (US111). A "state-carrying" event: a consumer never has to call Flight Routes back, and the
 * newest event of a route is enough to rebuild it.
 *
 * <pre>
 * { "routeId": "8c1d...", "originIata": "OPO", "destinationIata": "LIS", "distance": 274.0,
 *   "minRange": 400.0, "minCapacity": 120, "estimatedFlightTime": 55, "status": "ACTIVE", "version": 0,
 *   "history": [ { "changeDate": "2026-11-18T10:00:00", "description": "Route created.", "newMinRange": 400.0,
 *                  "newMinCapacity": 120, "newEstimatedFlightTime": 55, "newStatus": "ACTIVE" } ] }
 * </pre>
 *
 * @param version the route's optimistic-lock version on the owner replica: it grows with every update of the
 *                route, so a consumer can ignore an event older than what it already has.
 * @param history every change of the route, oldest first (may be empty for consumers that do not need it).
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
        Long version,
        List<HistoryEntry> history) {

    public RouteEventPayload {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** The state of a route of the write model, after it was flushed (so {@code version} is final). */
    public static RouteEventPayload of(FlightRoute route) {
        return new RouteEventPayload(route.getId(), route.getOriginIata(), route.getDestinationIata(),
                route.getDistance().getDistance(), route.getRequirements().getMinRange(),
                route.getRequirements().getMinCapacity(), route.getEstimatedFlightTime().getDurationMinutes(),
                route.getStatus().getState(), route.getVersion(),
                route.getHistoryLog().stream().map(HistoryEntry::of).toList());
    }

    /** One entry of the route history: only the attributes that changed are filled in. */
    public record HistoryEntry(
            LocalDateTime changeDate,
            String description,
            Double previousMinRange,
            Integer previousMinCapacity,
            Integer previousEstimatedFlightTime,
            String previousStatus,
            Double newMinRange,
            Integer newMinCapacity,
            Integer newEstimatedFlightTime,
            String newStatus) {

        static HistoryEntry of(RouteHistory h) {
            return new HistoryEntry(h.getChangeDate(), h.getDescription(),
                    h.getPreviousMinRange(), h.getPreviousMinCapacity(), h.getPreviousEstimatedFlightTime(),
                    h.getPreviousStatus(), h.getNewMinRange(), h.getNewMinCapacity(), h.getNewEstimatedFlightTime(),
                    h.getNewStatus());
        }
    }
}
