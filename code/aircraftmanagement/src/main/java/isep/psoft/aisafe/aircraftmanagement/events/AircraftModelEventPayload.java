package isep.psoft.aisafe.aircraftmanagement.events;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;

/**
 * Payload of {@code AircraftModelCreated} and {@code AircraftModelUpdated}: the full specification of the model after
 * the change. {@code modelName}, {@code maximumRange} (km) and {@code version} are the fields agreed in
 * Integration-Contracts section 9.4; the others are extra fields for consumers that need them.
 *
 * <pre>
 * { "modelName": "A320", "manufacturer": "Airbus", "seatingCapacity": 180, "fuelCapacity": 24210.0,
 *   "maximumRange": 6100.0, "cruisingSpeed": 828.0, "version": 0 }
 * </pre>
 *
 * @param version the model's optimistic-lock version on its owner replica.
 */
public record AircraftModelEventPayload(
        String modelName,
        String manufacturer,
        Integer seatingCapacity,
        Double fuelCapacity,
        Double maximumRange,
        Double cruisingSpeed,
        Long version) {

    /** The state of a model of the write model, after it was flushed (so {@code version} is final). */
    public static AircraftModelEventPayload of(AircraftModel model) {
        return new AircraftModelEventPayload(model.getModelName(), model.getManufacturer(),
                model.getSeatingCapacity(), model.getFuelCapacity(), model.getMaximumRange(),
                model.getCruisingSpeed(), model.getVersion());
    }
}
