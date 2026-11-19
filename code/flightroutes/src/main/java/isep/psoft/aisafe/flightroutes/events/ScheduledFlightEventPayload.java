package isep.psoft.aisafe.flightroutes.events;

import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Payload of {@code ScheduledFlightCreated} and {@code ScheduledFlightStatusChanged}: the full state of the flight
 * after the change, including the copy of the route data taken when it was scheduled.
 *
 * <pre>
 * { "flightId": "5b2e...", "aircraftRegistration": "CS-TUA", "routeId": "8c1d...", "originIata": "OPO",
 *   "destinationIata": "LIS", "estimatedFlightTime": 55, "date": "2026-11-02", "time": "08:00:00",
 *   "status": "SCHEDULED", "version": 0 }
 * </pre>
 *
 * @param version the flight's optimistic-lock version on the replica that owns it.
 */
public record ScheduledFlightEventPayload(
        String flightId,
        String aircraftRegistration,
        String routeId,
        String originIata,
        String destinationIata,
        Integer estimatedFlightTime,
        LocalDate date,
        LocalTime time,
        String status,
        Long version) {

    public static ScheduledFlightEventPayload of(ScheduledFlight flight) {
        return new ScheduledFlightEventPayload(flight.getId(), flight.getAircraftRegistration(),
                flight.getRoute().getRouteId(), flight.getRoute().getOriginIata(),
                flight.getRoute().getDestinationIata(), flight.getRoute().getEstimatedFlightTimeMinutes(),
                flight.getSchedule().getDate(), flight.getSchedule().getTime(), flight.getStatus().getState(),
                flight.getVersion());
    }
}
