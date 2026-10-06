package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.uri;

@Service
@RequiredArgsConstructor
public class SearchFlightRoutesService {

    private final FlightRouteRepository routeRepository;
    private final RouteReplicaQueries replicas;

    // US113 — rota local ou de uma réplica peer; "not found" em todas → 404.
    @Transactional(readOnly = true)
    public RouteSummary getRouteById(String id) {
        return replicas.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));
    }

    // US114 — pesquisa em todas as réplicas (sem parâmetros = todas as rotas).
    @Transactional(readOnly = true)
    public List<RouteSummary> searchRoutes(String origin, String dest) {
        String o = normalize(origin);
        String d = normalize(dest);

        List<FlightRoute> local;
        if (o != null && d != null) {
            local = routeRepository.findByOriginAndDestination(o, d);
        } else if (o != null) {
            local = routeRepository.findByOrigin(o);
        } else if (d != null) {
            local = routeRepository.findByDestination(d);
        } else {
            local = (List<FlightRoute>) routeRepository.findAll();
        }
        return replicas.withPeers(local, uri("/api/routes/search", "origin", o, "dest", d));
    }

    // US203 - rotas ativas compatíveis com o alcance/capacidade de uma aeronave (todas as réplicas).
    // Exposto para o microsserviço Aircraft Management.
    @Transactional(readOnly = true)
    public List<RouteSummary> findCompatibleRoutes(Double range, Integer capacity) {
        return replicas.withPeers(routeRepository.findCompatibleRoutes(range, capacity),
                uri("/api/routes/compatible", "range", range, "capacity", capacity));
    }

    private static String normalize(String iata) {
        return iata == null || iata.isBlank() ? null : iata.trim().toUpperCase();
    }
}
