package isep.psoft.aisafe.flightroutes.events.external;

/**
 * Expected payload of {@code AirportCreated}, {@code AirportUpdated} and {@code AirportStatusChanged} (published by
 * Airports): the full state of the airport after the change. Unknown extra fields are ignored.
 *
 * <pre>
 * { "iataCode": "OPO", "latitude": 41.2481, "longitude": -8.6814, "status": "OPERATIONAL", "version": 2 }
 * </pre>
 *
 * @param status  OPERATIONAL or any other status of the Airports service (only OPERATIONAL allows flights)
 * @param version grows with every change of the airport (older events are ignored)
 */
public record AirportEventPayload(String iataCode, Double latitude, Double longitude, String status, Long version) {
}
