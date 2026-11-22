package isep.psoft.aisafe.flightroutes.events.external;

/**
 * Expected payload of {@code AircraftModelCreated} and {@code AircraftModelUpdated} (published by Aircraft
 * Management). Unknown extra fields (manufacturer, seatingCapacity, ...) are ignored.
 *
 * <pre>
 * { "modelName": "A320", "maximumRange": 6100.0, "version": 0 }
 * </pre>
 */
public record AircraftModelEventPayload(String modelName, Double maximumRange, Long version) {
}
