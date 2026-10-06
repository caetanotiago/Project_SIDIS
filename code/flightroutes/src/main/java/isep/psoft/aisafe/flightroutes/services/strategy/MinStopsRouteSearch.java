package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
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
    public List<Itinerary> search(List<RouteSummary> activeRoutes, String originIata, String destIata) {
        // Build the connectivity graph: origin IATA -> outgoing routes.
        Map<String, List<RouteSummary>> adjacency = new HashMap<>();
        for (RouteSummary r : activeRoutes) {
            adjacency.computeIfAbsent(r.originIata(), k -> new ArrayList<>()).add(r);
        }

        List<Itinerary> results = new ArrayList<>();
        Queue<List<RouteSummary>> queue = new LinkedList<>();

        // Seed with the routes departing from the origin.
        for (RouteSummary r : adjacency.getOrDefault(originIata, List.of())) {
            queue.add(List.of(r));
        }

        Integer solutionLength = null; // number of legs of the first (minimal) solution found

        while (!queue.isEmpty()) {
            List<RouteSummary> path = queue.poll();

            // Once a minimal solution is found, stop processing longer paths.
            if (solutionLength != null && path.size() > solutionLength) {
                break;
            }

            RouteSummary last = path.get(path.size() - 1);
            String currentDest = last.destinationIata();

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
            for (RouteSummary leg : path) {
                visited.add(leg.destinationIata());
            }

            for (RouteSummary next : adjacency.getOrDefault(currentDest, List.of())) {
                String nextDest = next.destinationIata();
                if (!visited.contains(nextDest)) {
                    List<RouteSummary> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    queue.add(newPath);
                }
            }
        }

        return results;
    }
}
