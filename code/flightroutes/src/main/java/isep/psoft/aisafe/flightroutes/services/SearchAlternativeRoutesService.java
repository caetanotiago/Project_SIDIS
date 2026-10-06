package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import isep.psoft.aisafe.flightroutes.services.strategy.RouteSearchStrategy;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * US216 - Searches for alternative routes (itineraries) between two airports using a
 * pluggable algorithm (Strategy Pattern; default = fewest stops). The graph is built with the
 * active routes of EVERY replica, so an itinerary may combine legs stored in different replicas.
 */
@Service
@RequiredArgsConstructor
public class SearchAlternativeRoutesService {

    private final RouteReplicaQueries replicas;
    private final AirportClient airportClient;
    private final RouteSearchStrategy routeSearchStrategy;

    public List<Itinerary> searchAlternatives(String origin, String dest) {
        String originCode = origin == null ? null : origin.toUpperCase();
        String destCode = dest == null ? null : dest.toUpperCase();

        // 404 - origin and destination airports must exist
        if (originCode == null || !airportClient.exists(originCode)) {
            throw new EntityNotFoundException("Airport not found: " + origin);
        }
        if (destCode == null || !airportClient.exists(destCode)) {
            throw new EntityNotFoundException("Airport not found: " + dest);
        }

        return routeSearchStrategy.search(replicas.activeNetwork(), originCode, destCode);
    }
}
