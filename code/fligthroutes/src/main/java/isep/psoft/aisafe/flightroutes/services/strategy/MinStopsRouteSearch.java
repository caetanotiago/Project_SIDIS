package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Default search algorithm (US216): Breadth-First Search returning the itinerary/
 * itineraries with the fewest number of stops between origin and destination.
 */
@Component
public class MinStopsRouteSearch implements RouteSearchStrategy {

    @Override
    public List<Itinerary> search(List<FlightRoute> activeRoutes, String originIata, String destIata) {
        // Build the connectivity graph: origin IATA -> outgoing routes.
        Map<String, List<FlightRoute>> adjacency = new HashMap<>();
        for (FlightRoute r : activeRoutes) {
            adjacency.computeIfAbsent(r.getOriginIata(), k -> new ArrayList<>()).add(r);
        }

        List<Itinerary> results = new ArrayList<>();
        Queue<List<FlightRoute>> queue = new LinkedList<>();

        // Seed with the routes departing from the origin.
        for (FlightRoute r : adjacency.getOrDefault(originIata, List.of())) {
            queue.add(List.of(r));
        }

        Integer solutionLength = null; // number of legs of the first (minimal) solution found

        while (!queue.isEmpty()) {
            List<FlightRoute> path = queue.poll();

            // Once a minimal solution is found, stop processing longer paths.
            if (solutionLength != null && path.size() > solutionLength) {
                break;
            }

            FlightRoute last = path.get(path.size() - 1);
            String currentDest = last.getDestinationIata();

            if (currentDest.equals(destIata)) {
                results.add(new Itinerary(path));
                solutionLength = path.size();
                continue;
            }

            // Do not expand beyond the minimal solution depth.
            if (solutionLength != null) {
                continue;
            }

            // Visited airports along this path (avoid cycles).
            Set<String> visited = new HashSet<>();
            visited.add(originIata);
            for (FlightRoute leg : path) {
                visited.add(leg.getDestinationIata());
            }

            for (FlightRoute next : adjacency.getOrDefault(currentDest, List.of())) {
                String nextDest = next.getDestinationIata();
                if (!visited.contains(nextDest)) {
                    List<FlightRoute> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    queue.add(newPath);
                }
            }
        }

        return results;
    }
}
