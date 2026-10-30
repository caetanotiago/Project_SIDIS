package isep.psoft.aisafe.airports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US106a — modelo de aeronave a certificar no aeroporto (ex. "A320"). */
public record AddCertificationDTO(
        @NotBlank(message = "Aircraft model is required")
        @Size(max = 100, message = "Aircraft model must have at most 100 characters")
        String aircraftModel) {
}
