package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchFlightRoutesService {

    private final FlightRouteRepository routeRepository;

    // Para a US113 — "not found" devolve 404 (consistente com US111/US112).
    public FlightRoute getRouteById(String id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));
    }

    // Para a US114
    public List<FlightRoute> searchRoutes(String origin, String dest) {
        boolean hasOrigin = (origin != null && !origin.isBlank());
        boolean hasDest = (dest != null && !dest.isBlank());

        if (hasOrigin && hasDest) {
            return routeRepository.findByOriginAndDestination(origin, dest);
        } else if (hasOrigin) {
            return routeRepository.findByOrigin(origin);
        } else if (hasDest) {
            return routeRepository.findByDestination(dest);
        } else {
            // Se não passar parâmetros, assume-se que o user quer que se devolva todas as rotas.
            return (List<FlightRoute>) routeRepository.findAll();
        }
    }

    // US203 - rotas ativas compatíveis com o alcance/capacidade de uma aeronave.
    // Exposto para o microsserviço Aircraft Management (antes usava o FlightRouteRepository).
    public List<FlightRoute> findCompatibleRoutes(Double range, Integer capacity) {
        return routeRepository.findCompatibleRoutes(range, capacity);
    }
}