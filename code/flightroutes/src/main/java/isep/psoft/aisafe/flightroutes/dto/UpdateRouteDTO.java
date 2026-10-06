package isep.psoft.aisafe.flightroutes.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateRouteDTO {

    @Min(value = 1, message = "Estimated flight time must be positive")
    private Integer estimatedFlightTime;

    @Min(value = 1, message = "Minimum range must be positive")
    private Double minRange;

    @Min(value = 1, message = "Minimum capacity must be positive")
    private Integer minCapacity;

    // Para desativar a rota, enviamos o status "INACTIVE"
    private String status; 
}