package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.controllers.FlightRouteController;
import isep.psoft.aisafe.flightroutes.controllers.ScheduledFlightController;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
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

    public List<ScheduledFlightDTO> toModelList(List<ScheduledFlight> flights) {
        return flights.stream().map(this::toModel).toList();
    }

    /**
     * (Re)cria os links HATEOAS a apontar para ESTA réplica. Usado também nos DTOs recebidos de
     * peers: os links continuam válidos aqui, porque esta réplica encaminha o pedido ao dono.
     */
    public ScheduledFlightDTO withLinks(ScheduledFlightDTO dto) {
        dto.removeLinks();
        dto.add(linkTo(methodOn(ScheduledFlightController.class)
                .getScheduledFlightById(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(FlightRouteController.class)
                .getRouteById(dto.getRouteID())).withRel("route"));
        return dto;
    }
}
