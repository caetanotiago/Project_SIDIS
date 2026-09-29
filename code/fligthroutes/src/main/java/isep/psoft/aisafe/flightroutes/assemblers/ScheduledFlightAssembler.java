package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.controllers.FlightRouteController;
import isep.psoft.aisafe.flightroutes.controllers.ScheduledFlightController;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ScheduledFlightAssembler
        implements RepresentationModelAssembler<ScheduledFlight, ScheduledFlightDTO> {

    @Override
    @NonNull
    public ScheduledFlightDTO toModel(@NonNull ScheduledFlight flight) {
        ScheduledFlightDTO dto = new ScheduledFlightDTO();
        dto.setId(flight.getId());
        dto.setAircraftRegistration(flight.getAircraftRegistration());
        dto.setRouteID(flight.getRoute().getId());
        dto.setOriginIATA(flight.getRoute().getOriginIata());
        dto.setDestIATA(flight.getRoute().getDestinationIata());
        dto.setDate(flight.getSchedule().getDate());
        dto.setTime(flight.getSchedule().getTime());
        dto.setStatus(flight.getStatus().getState());

        dto.add(linkTo(methodOn(ScheduledFlightController.class)
                .getScheduledFlightById(flight.getId())).withSelfRel());
        dto.add(linkTo(methodOn(FlightRouteController.class)
                .getRouteById(flight.getRoute().getId())).withRel("route"));
        return dto;
    }
}
