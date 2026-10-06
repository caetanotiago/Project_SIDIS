package isep.psoft.aisafe.flightroutes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateRouteDTO {

    @NotBlank(message = "Origin airport IATA code is required")
    @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
    private String originIATA;

    @NotBlank(message = "Destination airport IATA code is required")
    @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
    private String destIATA;

    @NotNull(message = "Estimated flight time is required")
    @Min(value = 1, message = "Estimated flight time must be positive")
    private Integer estimatedFlightTime;

    @NotNull(message = "Minimum range is required")
    @Min(value = 1, message = "Minimum range must be positive")
    private Double minRange;

    @NotNull(message = "Minimum capacity is required")
    @Min(value = 1, message = "Minimum capacity must be positive")
    private Integer minCapacity;
}