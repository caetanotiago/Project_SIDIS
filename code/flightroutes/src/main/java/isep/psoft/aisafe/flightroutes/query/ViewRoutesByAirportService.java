package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * US209 (query) - every route departing from or arriving at an airport, from the route read model. Airports is
 * asked only to validate that the airport exists (B2; local airport read model once Airports publishes events).
 */
@Service
@RequiredArgsConstructor
public class ViewRoutesByAirportService {

    private final AirportClient airportClient;
    private final ReplicatedRouteRepository routes;

    @Transactional(readOnly = true)
    public List<RouteSummary> findRoutesByAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        if (!airportClient.exists(code)) {
            throw new EntityNotFoundException("Airport not found: " + iataCode);
        }
        return routes.findByOriginOrDestination(code).stream().map(ReplicatedRoute::toSummary).toList();
    }
}
