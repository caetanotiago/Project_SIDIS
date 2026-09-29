package isep.psoft.aisafe.flightroutes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteLegDTO {
    private String routeID;
    private String originIATA;
    private String destIATA;
    private Double distance;
}
