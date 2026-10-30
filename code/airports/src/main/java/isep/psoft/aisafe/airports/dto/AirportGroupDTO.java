package isep.psoft.aisafe.airports.dto;

import java.util.List;

/** US211 — um grupo de aeroportos (uma região ou um país) com os aeroportos que lhe pertencem. */
public record AirportGroupDTO(
        String group,
        int count,
        List<AirportDTO> airports) {
}
