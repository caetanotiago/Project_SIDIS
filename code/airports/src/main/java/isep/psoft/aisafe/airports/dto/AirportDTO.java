package isep.psoft.aisafe.airports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Set;

/**
 * Representação de um aeroporto devolvida pela API (US107) e trocada entre réplicas.
 *
 * <p>Contrato B2 (Flight Routes → Airports): {@code iataCode}, {@code latitude}, {@code longitude}
 * e {@code status} são obrigatórios e não podem mudar de nome. Os restantes campos são ignorados
 * pelo Flight Routes.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // tolera campos extra nas respostas de outras réplicas
public record AirportDTO(
        String iataCode,
        String name,
        String city,
        String country,
        String region,
        String timezone,
        Double latitude,
        Double longitude,
        String status,
        List<RunwayDTO> runways,
        Set<String> certifiedModels,
        Integer terminals,
        Integer gates,
        Set<String> services,
        List<String> photoUrls,
        String operationalHours,
        String contactEmail,
        String contactPhone) {
}
