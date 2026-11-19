package isep.psoft.aisafe.flightroutes.events.external;

/**
 * Expected payload of {@code AircraftRegistered} and {@code AircraftStatusChanged} (published by Aircraft
 * Management): the full state of the aircraft after the change. Unknown extra fields are ignored.
 *
 * <pre>
 * { "registrationNumber": "CS-TUA", "modelName": "A320", "seatingCapacity": 180, "status": "AVAILABLE", "version": 3 }
 * </pre>
 *
 * @param status  AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE or INACTIVE
 * @param version grows with every change of the aircraft (older events are ignored)
 */
public record AircraftEventPayload(String registrationNumber, String modelName, Integer seatingCapacity,
                                   String status, Long version) {
}
