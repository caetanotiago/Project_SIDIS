package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.assemblers.FlightRouteAssembler;
import isep.psoft.aisafe.flightroutes.dto.RouteHistoryDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** US111 (query) - history of a route, carried by its events and stored in the route read model of every replica. */
@Service
@RequiredArgsConstructor
public class GetRouteHistoryService {

    private final ReplicatedRouteRepository routes;
    private final FlightRouteAssembler assembler;

    @Transactional(readOnly = true)
    public List<RouteHistoryDTO> getRouteHistory(String routeId) {
        return routes.findById(routeId)
                .map(route -> assembler.toHistoryDTOList(route.getHistory()))
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + routeId));
    }
}
