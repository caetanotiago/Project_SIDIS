package isep.psoft.aisafe.airports.dto;

import java.util.List;

// Grupo de aeroportos (US211)
public record AirportGroupDTO(
        String group,
        int count,
        List<AirportDTO> airports) {
}
