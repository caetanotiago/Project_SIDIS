package isep.psoft.aisafe.flightroutes.events.external;

/**
 * Expected payload of {@code AircraftModelCreated} (published by Aircraft Management).
 *
 * <pre>
 * { "modelName": "A320", "maximumRange": 6100.0, "version": 0 }
 * </pre>
 */
public record AircraftModelEventPayload(String modelName, Double maximumRange, Long version) {
}
