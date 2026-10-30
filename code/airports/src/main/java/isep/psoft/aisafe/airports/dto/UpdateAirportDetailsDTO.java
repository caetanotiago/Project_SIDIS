package isep.psoft.aisafe.airports.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Set;

/**
 * US208 — atualização parcial dos detalhes do aeroporto: horário, contactos e instalações.
 * Campos ausentes (null) não são alterados; uma string vazia limpa o campo.
 */
public record UpdateAirportDetailsDTO(
        String operationalHours,

        @Email(message = "Contact email must be a valid email address")
        String contactEmail,

        String contactPhone,

        @Min(value = 0, message = "Number of terminals cannot be negative")
        Integer terminals,

        @Min(value = 0, message = "Number of gates cannot be negative")
        Integer gates,

        Set<String> services,

        List<String> photoUrls) {
}
