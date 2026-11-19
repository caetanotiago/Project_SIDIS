package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** US113, US114, US203 (query): answered from this replica's route read model only (no peer calls). */
@Service
@RequiredArgsConstructor
public class SearchFlightRoutesService {

    private final ReplicatedRouteRepository routes;

    // US113
    @Transactional(readOnly = true)
    public RouteSummary getRouteById(String id) {
        return routes.findById(id).map(ReplicatedRoute::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));
    }

    // US114 - no parameters = every route
    @Transactional(readOnly = true)
    public List<RouteSummary> searchRoutes(String origin, String dest) {
        String o = normalize(origin);
        String d = normalize(dest);
        List<ReplicatedRoute> found;
        if (o != null && d != null) {
            found = routes.findByOriginAndDestination(o, d);
        } else if (o != null) {
            found = routes.findByOrigin(o);
        } else if (d != null) {
            found = routes.findByDestination(d);
        } else {
            found = routes.findAllOrdered();
        }
        return found.stream().map(ReplicatedRoute::toSummary).toList();
    }

    // US203 (B6 provider, called by Aircraft Management): active routes compatible with an aircraft
    @Transactional(readOnly = true)
    public List<RouteSummary> findCompatibleRoutes(Double range, Integer capacity) {
        return routes.findCompatibleRoutes(range, capacity).stream().map(ReplicatedRoute::toSummary).toList();
    }

    private static String normalize(String iata) {
        return iata == null || iata.isBlank() ? null : iata.trim().toUpperCase();
    }
}
