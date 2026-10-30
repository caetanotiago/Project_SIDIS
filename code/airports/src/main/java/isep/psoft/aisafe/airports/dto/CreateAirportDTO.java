package isep.psoft.aisafe.airports.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Set;

/**
 * Pedido de registo de um aeroporto.
 * US106: IATA, nome, cidade, país, fuso horário, coordenadas e pistas (obrigatórios).
 * US207: instalações e fotos (opcionais). Região e contactos (US208) também podem vir já no registo.
 */
public record CreateAirportDTO(
        @NotBlank(message = "IATA code is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "IATA code must be exactly 3 letters")
        String iataCode,

        @NotBlank(message = "Airport name is required")
        String name,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Country is required")
        String country,

        String region,

        @NotBlank(message = "Timezone is required")
        String timezone,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @NotEmpty(message = "At least one runway is required")
        List<@Valid RunwayDTO> runways,

        @Min(value = 0, message = "Number of terminals cannot be negative")
        Integer terminals,

        @Min(value = 0, message = "Number of gates cannot be negative")
        Integer gates,

        Set<String> services,

        List<String> photoUrls,

        String operationalHours,

        @Email(message = "Contact email must be a valid email address")
        String contactEmail,

        String contactPhone) {
}
