package isep.psoft.aisafe.airports.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAirportStatusDTO(
        @NotBlank(message = "Status is required")
        String status) {
}
