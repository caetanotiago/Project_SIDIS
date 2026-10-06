package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.pathSegment;

// US209 - Visualizar todas as rotas que partem de ou chegam a um aeroporto específico (todas as réplicas).
// Consulta o microsserviço Airports apenas para validar a existência do aeroporto.
@Service
@RequiredArgsConstructor
public class ViewRoutesByAirportService {

    private final AirportClient airportClient;
    private final FlightRouteRepository flightRouteRepository;
    private final RouteReplicaQueries replicas;

    @Transactional(readOnly = true)
    public List<RouteSummary> findRoutesByAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        // A validação é feita uma vez, pela réplica que recebeu o pedido do cliente.
        if (!replicas.isPeerRequest() && !airportClient.exists(code)) {
            throw new EntityNotFoundException("Airport not found: " + iataCode);
        }
        return replicas.withPeers(flightRouteRepository.findByOriginOrDestination(code),
                "/api/routes/by-airport/" + pathSegment(code));
    }
}
