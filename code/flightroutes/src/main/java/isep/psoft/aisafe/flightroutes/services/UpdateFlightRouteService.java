package isep.psoft.aisafe.flightroutes.services;

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

import java.util.Optional;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.pathSegment;

/**
 * US112 - atualizar/desativar uma rota. A escrita é feita SEMPRE na réplica dona da rota (a que a
 * guarda): se a rota não for local, o comando é encaminhado (unicast) para a réplica que a tem,
 * para não existirem duas versões da mesma rota. O @Version garante a deteção de escritas
 * concorrentes na réplica dona (409).
 */
@Service
@RequiredArgsConstructor
public class UpdateFlightRouteService {

    private final FlightRouteRepository routeRepository;
    private final PeerClient peerClient;
    private final FlightRouteAssembler assembler;

    @Transactional
    public FlightRouteDTO updateRoute(String id, UpdateRouteDTO dto) {
        Optional<FlightRoute> local = routeRepository.findById(id);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH, "/api/routes/" + pathSegment(id), dto, FlightRouteDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));
        }
        FlightRoute route = local.get();

        // Atualização parcial dos detalhes operacionais (qualquer subconjunto dos campos).
        if (dto.getMinRange() != null || dto.getMinCapacity() != null || dto.getEstimatedFlightTime() != null) {
            route.updateDetails(dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime());
        }

        // Mudança de estado (ativar/desativar) — pode vir no mesmo pedido que os detalhes.
        if (dto.getStatus() != null) {
            route.changeStatus(dto.getStatus());
        }

        return assembler.toDTO(routeRepository.save(route));
    }
}
