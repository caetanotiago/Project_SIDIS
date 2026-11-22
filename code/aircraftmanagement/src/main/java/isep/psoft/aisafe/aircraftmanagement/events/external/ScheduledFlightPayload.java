package isep.psoft.aisafe.aircraftmanagement.events.external;

/**
 * The fields of Flight Routes' {@code ScheduledFlightEventPayload} that Aircraft Management reads (tolerant reader).
 *
 * <pre>
 * { "flightId": "5b2e...", "aircraftRegistration": "CS-TUA", "routeId": "8c1d...", "originIata": "OPO",
 *   "destinationIata": "LIS", "estimatedFlightTime": 55, "date": "2026-11-02", "time": "08:00:00",
 *   "status": "SCHEDULED", "version": 0 }
 * </pre>
 *
 * @param estimatedFlightTime minutes, copied by Flight Routes from the route when the flight was scheduled
 */
public record ScheduledFlightPayload(String flightId, String aircraftRegistration, String routeId,
                                     Integer estimatedFlightTime, String status, Long version) {
}
