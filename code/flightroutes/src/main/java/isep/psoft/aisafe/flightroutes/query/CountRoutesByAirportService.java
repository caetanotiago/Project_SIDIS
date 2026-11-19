package isep.psoft.aisafe.flightroutes.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * US210 (query, B5 provider) - number of routes per airport (origin + destination), for the Airports service, which
 * computes the busiest airports. The read model holds the routes of every replica, so no partial counts are summed.
 */
@Service
@RequiredArgsConstructor
public class CountRoutesByAirportService {

    private final ReplicatedRouteRepository routes;

    @Transactional(readOnly = true)
    public Map<String, Long> countRoutesByAirport() {
        Map<String, Long> counts = new HashMap<>();
        merge(counts, routes.countByOrigin());
        merge(counts, routes.countByDestination());
        return counts;
    }

    private static void merge(Map<String, Long> counts, List<Object[]> rows) {
        for (Object[] row : rows) {
            counts.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
    }
}
