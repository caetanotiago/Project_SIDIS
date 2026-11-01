package isep.psoft.aisafe.aircraftmanagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

/** Modelo de aeronave (US101, US201). maximumRange em km (decisão Q9 do contrato). */
@Entity
public class AircraftModel {

    @Id
    private String modelName;

    private String manufacturer;
    private int seatingCapacity;
    private double fuelCapacity;
    private double maximumRange;
    private double cruisingSpeed;

    @Version
    private Long version;

    protected AircraftModel() {
        // JPA
    }

    public AircraftModel(String modelName, String manufacturer, int seatingCapacity,
                         double fuelCapacity, double maximumRange, double cruisingSpeed) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be empty");
        }
        if (manufacturer == null || manufacturer.isBlank()) {
            throw new IllegalArgumentException("Manufacturer cannot be empty");
        }
        if (seatingCapacity <= 0 || fuelCapacity <= 0 || maximumRange <= 0 || cruisingSpeed <= 0) {
            throw new IllegalArgumentException(
                    "Seating capacity, fuel capacity, maximum range and cruising speed must be positive");
        }
        this.modelName = modelName;
        this.manufacturer = manufacturer;
        this.seatingCapacity = seatingCapacity;
        this.fuelCapacity = fuelCapacity;
        this.maximumRange = maximumRange;
        this.cruisingSpeed = cruisingSpeed;
    }

    /** US201: atualização parcial; o nome (identificador) é imutável. Parâmetros null = não alterar. */
    public void updateSpecifications(String manufacturer, Integer seatingCapacity, Double fuelCapacity,
                                     Double maximumRange, Double cruisingSpeed) {
        if (manufacturer != null && manufacturer.isBlank()) {
            throw new IllegalArgumentException("Manufacturer cannot be empty");
        }
        if ((seatingCapacity != null && seatingCapacity <= 0)
                || (fuelCapacity != null && fuelCapacity <= 0)
                || (maximumRange != null && maximumRange <= 0)
                || (cruisingSpeed != null && cruisingSpeed <= 0)) {
            throw new IllegalArgumentException(
                    "Seating capacity, fuel capacity, maximum range and cruising speed must be positive");
        }
        if (manufacturer != null) this.manufacturer = manufacturer;
        if (seatingCapacity != null) this.seatingCapacity = seatingCapacity;
        if (fuelCapacity != null) this.fuelCapacity = fuelCapacity;
        if (maximumRange != null) this.maximumRange = maximumRange;
        if (cruisingSpeed != null) this.cruisingSpeed = cruisingSpeed;
    }

    public AircraftModelSummary toSummary() {
        return new AircraftModelSummary(modelName, manufacturer, seatingCapacity,
                fuelCapacity, maximumRange, cruisingSpeed);
    }

    public String getModelName() { return modelName; }
    public String getManufacturer() { return manufacturer; }
    public int getSeatingCapacity() { return seatingCapacity; }
    public double getFuelCapacity() { return fuelCapacity; }
    public double getMaximumRange() { return maximumRange; }
    public double getCruisingSpeed() { return cruisingSpeed; }
}
