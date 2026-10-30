package isep.psoft.aisafe.airports.dto;

import jakarta.validation.constraints.NotBlank;

/** US109 — novo estado operacional: OPERATIONAL, CLOSED ou UNDER_MAINTENANCE. */
public record UpdateAirportStatusDTO(
        @NotBlank(message = "Status is required")
        String status) {
}
