package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import isep.psoft.aisafe.flightroutes.services.strategy.RouteSearchStrategy;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * US216 (query) - alternative routes (itineraries) between two airports, using a pluggable algorithm (Strategy
 * Pattern; default = fewest stops) over the active routes of the read model, so an itinerary may combine legs owned
 * by different replicas.
 */
@Service
@RequiredArgsConstructor
public class SearchAlternativeRoutesService {

    private final ReplicatedRouteRepository routes;
    private final AirportClient airportClient;
    private final RouteSearchStrategy routeSearchStrategy;

    @Transactional(readOnly = true)
    public List<Itinerary> searchAlternatives(String origin, String dest) {
        String originCode = origin == null ? null : origin.toUpperCase();
        String destCode = dest == null ? null : dest.toUpperCase();

        // 404 - origin and destination airports must exist (B2)
        if (originCode == null || !airportClient.exists(originCode)) {
            throw new EntityNotFoundException("Airport not found: " + origin);
        }
        if (destCode == null || !airportClient.exists(destCode)) {
            throw new EntityNotFoundException("Airport not found: " + dest);
        }
        return routeSearchStrategy.search(routes.findAllActive().stream().map(ReplicatedRoute::toSummary).toList(),
                originCode, destCode);
    }
}
