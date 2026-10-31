package isep.psoft.aisafe.aircraftmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Rota devolvida pelo Flight Routes em GET /api/routes/compatible (ponte B6). */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompatibleRouteDTO {
    private String id;
    private String originIATA;
    private String destIATA;
    private Double distance;
    private Double minRange;
    private Integer minCapacity;
    private Integer estimatedFlightTime;
    private String status;
    private Integer usageCount;
}
