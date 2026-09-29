package isep.psoft.aisafe.flightroutes.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FlightRouteDTO {
    private String id;
    private String originIATA;
    private String destIATA;
    private Double distance;
    private Double minRange;
    private Integer minCapacity;
    private Integer estimatedFlightTime;
    private String status;
    private Long usageCount; // US214 - popularidade (nº de voos agendados); null quando não aplicável
}