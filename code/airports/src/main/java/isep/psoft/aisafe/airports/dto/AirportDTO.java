package isep.psoft.aisafe.airports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Set;

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
