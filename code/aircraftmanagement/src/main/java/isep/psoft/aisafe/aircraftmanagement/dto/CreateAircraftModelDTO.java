package isep.psoft.aisafe.aircraftmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US101. */
@Data
@NoArgsConstructor
public class CreateAircraftModelDTO {

    @NotBlank(message = "Model name is required")
    private String modelName;

    @NotBlank(message = "Manufacturer is required")
    private String manufacturer;

    @NotNull(message = "Seating capacity is required")
    @Positive(message = "Seating capacity must be positive")
    private Integer seatingCapacity;

    @NotNull(message = "Fuel capacity is required")
    @Positive(message = "Fuel capacity must be positive")
    private Double fuelCapacity;

    @NotNull(message = "Maximum range is required")
    @Positive(message = "Maximum range must be positive")
    private Double maximumRange;

    @NotNull(message = "Cruising speed is required")
    @Positive(message = "Cruising speed must be positive")
    private Double cruisingSpeed;
}
