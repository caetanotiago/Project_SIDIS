package isep.psoft.aisafe.flightroutes.dto;

import isep.psoft.aisafe.flightroutes.query.ReplicatedRoute;

import java.time.Instant;

/**
 * A route as known by this replica's event-built read model. Same route fields as {@link FlightRouteDTO}, plus
 * where it came from: the owner replica, its version there and the last event applied.
 */
public record ReplicatedRouteDTO(
        String id,
        String originIATA,
        String destIATA,
        Double distance,
        Double minRange,
        Integer minCapacity,
        Integer estimatedFlightTime,
        String status,
        Long version,
        String ownerInstance,
        String lastEventId,
        Instant lastEventAt) {

    public static ReplicatedRouteDTO of(ReplicatedRoute r) {
        return new ReplicatedRouteDTO(r.getId(), r.getOriginIata(), r.getDestinationIata(), r.getDistance(),
                r.getMinRange(), r.getMinCapacity(), r.getEstimatedFlightTime(), r.getStatus(), r.getSourceVersion(),
                r.getOwnerInstance(), r.getLastEventId(), r.getLastEventAt());
    }
}
