package isep.psoft.aisafe.airports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Pista, tanto no pedido de registo (US106) como na resposta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunwayDTO(
        @NotBlank(message = "Runway name is required")
        String name,

        @NotNull(message = "Runway length is required")
        @Min(value = 1, message = "Runway length must be positive")
        Integer lengthMeters,

        @NotNull(message = "Runway orientation is required")
        @Min(value = 0, message = "Runway orientation must be between 0 and 359")
        @Max(value = 359, message = "Runway orientation must be between 0 and 359")
        Integer orientation,

        String surface) {
}
