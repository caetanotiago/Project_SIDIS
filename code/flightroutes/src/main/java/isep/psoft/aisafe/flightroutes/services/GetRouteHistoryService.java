package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.assemblers.FlightRouteAssembler;
import isep.psoft.aisafe.flightroutes.dto.RouteHistoryDTO;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.pathSegment;

// US111 - histórico de uma rota. O histórico vive com a rota, na réplica que a guarda.
@Service
@RequiredArgsConstructor
public class GetRouteHistoryService {

    private static final ParameterizedTypeReference<List<RouteHistoryDTO>> HISTORY = new ParameterizedTypeReference<>() { };

    private final FlightRouteRepository routeRepository;
    private final PeerClient peerClient;
    private final FlightRouteAssembler assembler;

    @Transactional(readOnly = true)
    public List<RouteHistoryDTO> getRouteHistory(String routeId) {
        return routeRepository.findById(routeId)
                .map(route -> assembler.toHistoryDTOList(route.getHistoryLog()))
                .or(() -> peerClient.findFirst("/api/routes/" + pathSegment(routeId) + "/history", HISTORY))
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + routeId));
    }
}
