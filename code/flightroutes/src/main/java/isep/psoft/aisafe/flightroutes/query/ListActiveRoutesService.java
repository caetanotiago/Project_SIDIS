package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.services.strategy.RouteSortStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * US214 (query) - active routes sorted by a pluggable criterion (Strategy Pattern). Popularity = number of scheduled
 * flights on the route, from the flight read model (flights of every replica, wherever the route is stored).
 */
@Service
public class ListActiveRoutesService {

    private final ReplicatedRouteRepository routes;
    private final ScheduledFlightStatisticsService statistics;
    private final Map<String, RouteSortStrategy> strategies;

    public ListActiveRoutesService(ReplicatedRouteRepository routes,
                                   ScheduledFlightStatisticsService statistics,
                                   List<RouteSortStrategy> strategyBeans) {
        this.routes = routes;
        this.statistics = statistics;
        this.strategies = strategyBeans.stream()
                .collect(Collectors.toMap(RouteSortStrategy::key, s -> s));
    }

    @Transactional(readOnly = true)
    public List<RouteUsage> listActiveRoutes(String sortBy) {
        String key = (sortBy == null || sortBy.isBlank()) ? "popularity" : sortBy.toLowerCase();
        RouteSortStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Invalid sortBy value: '" + sortBy + "'. Allowed values: " + strategies.keySet());
        }
        List<RouteSummary> network = routes.findAllActive().stream().map(ReplicatedRoute::toSummary).toList();
        Map<String, Long> usage = statistics.usageByRoute();
        List<RouteUsage> usages = network.stream()
                .map(route -> new RouteUsage(route, usage.getOrDefault(route.id(), 0L)))
                .toList();
        return strategy.sort(usages);
    }
}
