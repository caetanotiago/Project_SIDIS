package isep.psoft.aisafe.airports.dto;

import jakarta.validation.constraints.NotBlank;

// Pedido de mudança de estado
public record UpdateAirportStatusDTO(
        @NotBlank(message = "Status is required")
        String status) {
}
