package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.controllers.FlightRouteController;
import isep.psoft.aisafe.flightroutes.controllers.ScheduledFlightController;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.query.ReplicatedScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

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
        dto.setRouteID(flight.getRoute().getRouteId());
        dto.setOriginIATA(flight.getRoute().getOriginIata());
        dto.setDestIATA(flight.getRoute().getDestinationIata());
        dto.setEstimatedFlightTime(flight.getRoute().getEstimatedFlightTimeMinutes());
        dto.setDate(flight.getSchedule().getDate());
        dto.setTime(flight.getSchedule().getTime());
        dto.setStatus(flight.getStatus().getState());
        return withLinks(dto);
    }

    /** Same representation, from the read model (flights of every replica). */
    public ScheduledFlightDTO toModel(ReplicatedScheduledFlight flight) {
        ScheduledFlightDTO dto = new ScheduledFlightDTO();
        dto.setId(flight.getId());
        dto.setAircraftRegistration(flight.getAircraftRegistration());
        dto.setRouteID(flight.getRouteId());
        dto.setOriginIATA(flight.getOriginIata());
        dto.setDestIATA(flight.getDestinationIata());
        dto.setEstimatedFlightTime(flight.getEstimatedFlightTime());
        dto.setDate(flight.getDate());
        dto.setTime(flight.getTime());
        dto.setStatus(flight.getStatus());
        return withLinks(dto);
    }

    /** HATEOAS links pointing to THIS replica: every replica can answer them from its own read models. */
    public ScheduledFlightDTO withLinks(ScheduledFlightDTO dto) {
        dto.removeLinks();
        dto.add(linkTo(methodOn(ScheduledFlightController.class)
                .getScheduledFlightById(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(FlightRouteController.class)
                .getRouteById(dto.getRouteID())).withRel("route"));
        return dto;
    }
}
