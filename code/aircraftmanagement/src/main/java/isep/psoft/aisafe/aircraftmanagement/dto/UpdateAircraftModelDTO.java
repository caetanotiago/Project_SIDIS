package isep.psoft.aisafe.aircraftmanagement.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US201: PATCH parcial; só os campos enviados são alterados. O nome do modelo não se altera. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAircraftModelDTO {

    private String manufacturer;

    @Positive(message = "Seating capacity must be positive")
    private Integer seatingCapacity;

    @Positive(message = "Fuel capacity must be positive")
    private Double fuelCapacity;

    @Positive(message = "Maximum range must be positive")
    private Double maximumRange;

    @Positive(message = "Cruising speed must be positive")
    private Double cruisingSpeed;

    public boolean isEmpty() {
        return manufacturer == null && seatingCapacity == null && fuelCapacity == null
                && maximumRange == null && cruisingSpeed == null;
    }
}
