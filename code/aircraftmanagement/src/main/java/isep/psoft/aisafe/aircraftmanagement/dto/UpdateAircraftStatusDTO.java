package isep.psoft.aisafe.aircraftmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Corpo do PATCH /api/aircrafts/{reg}/status (US105, ponte B3b): {"status": "UNDER_MAINTENANCE"}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAircraftStatusDTO {

    @NotBlank(message = "Status is required")
    private String status;
}
