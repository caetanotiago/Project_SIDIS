package isep.psoft.aisafe.airports.dto;

import java.util.List;

public record AirportGroupDTO(
        String group,
        int count,
        List<AirportDTO> airports) {
}
