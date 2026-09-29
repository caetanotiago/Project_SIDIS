package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import isep.psoft.aisafe.flightroutes.dto.ItineraryDTO;
import isep.psoft.aisafe.flightroutes.dto.RouteLegDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItineraryAssembler {

    public ItineraryDTO toDTO(Itinerary itinerary) {
        ItineraryDTO dto = new ItineraryDTO();
        List<RouteLegDTO> legs = itinerary.getLegs().stream()
                .map(r -> new RouteLegDTO(
                        r.getId(),
                        r.getOriginIata(),
                        r.getDestinationIata(),
                        r.getDistance().getDistance()))
                .collect(Collectors.toList());
        dto.setLegs(legs);
        dto.setNumberOfStops(itinerary.getNumberOfStops());
        dto.setTotalDistance(itinerary.getTotalDistance());
        return dto;
    }

    public List<ItineraryDTO> toDTOList(List<Itinerary> itineraries) {
        return itineraries.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
