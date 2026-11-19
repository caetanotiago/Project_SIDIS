package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.assemblers.FlightRouteAssembler;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.FlightRouteDTO;
import isep.psoft.aisafe.flightroutes.dto.UpdateRouteDTO;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * US112 (command) - update or deactivate a route. ONE OWNER PER ENTITY: the write is always executed by the replica
 * that stores the route in its write model. If the route is not local, the command is forwarded (unicast) to the
 * peer that owns it, so there are never two versions of the same route and each route has a single event producer
 * (which keeps its events in order). The owner's {@code @Version} detects concurrent writes (409).
 *
 * <p>Deactivation is a {@code RouteUpdated} with status INACTIVE (routes are never deleted).
 */
@Service
@RequiredArgsConstructor
public class UpdateFlightRouteService {

    private final FlightRouteRepository routeRepository;
    private final PeerClient peerClient;
    private final FlightRouteAssembler assembler;
    private final FlightRoutesEventEmitter events;

    @Transactional
    public FlightRouteDTO updateRoute(String id, UpdateRouteDTO dto) {
        Optional<FlightRoute> local = routeRepository.findById(id);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/routes/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8), dto,
                            FlightRouteDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));
        }

        FlightRoute route = local.get();
        // Partial update of the operational details (any subset of the fields).
        if (dto.getMinRange() != null || dto.getMinCapacity() != null || dto.getEstimatedFlightTime() != null) {
            route.updateDetails(dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime());
        }
        // Status change (activate/deactivate) - may come in the same request as the details.
        if (dto.getStatus() != null) {
            route.changeStatus(dto.getStatus());
        }

        // Flush first: the event carries the version incremented by this update.
        FlightRoute saved = routeRepository.saveAndFlush(route);
        events.routeUpdated(saved);
        return assembler.toDTO(saved);
    }
}
