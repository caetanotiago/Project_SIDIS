package isep.psoft.aisafe.aircraftmanagement.events;

/**
 * Payload of {@code AircraftSyncRequested} (internal to Aircraft Management, as {@code AirportSyncRequested} in
 * Airports).
 *
 * @param requestedBy replica that started and wants the current state of the aircraft and models of the others
 */
public record AircraftSyncRequest(String requestedBy) {
}
