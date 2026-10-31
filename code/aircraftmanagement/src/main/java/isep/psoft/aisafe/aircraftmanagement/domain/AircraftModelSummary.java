package isep.psoft.aisafe.aircraftmanagement.domain;

/** Read model imutável de um modelo de aeronave. */
public record AircraftModelSummary(String modelName, String manufacturer, int seatingCapacity,
                                   double fuelCapacity, double maximumRange, double cruisingSpeed) {
}
