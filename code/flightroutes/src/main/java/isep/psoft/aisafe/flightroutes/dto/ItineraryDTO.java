package isep.psoft.aisafe.flightroutes.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.util.List;

@Getter
@Setter
public class ItineraryDTO extends RepresentationModel<ItineraryDTO> {
    private List<RouteLegDTO> legs;
    private Integer numberOfStops;
    private Double totalDistance;
}
