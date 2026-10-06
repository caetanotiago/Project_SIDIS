package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.services.strategy.RouteSortStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * US214 - Lists active routes (of every replica) sorted by a pluggable criterion (Strategy Pattern).
 * Popularity = number of scheduled flights on the route, counted in every replica: a flight may be
 * stored in a different replica from its route.
 */
@Service
public class ListActiveRoutesService {

    private final RouteReplicaQueries replicas;
    private final ScheduledFlightStatisticsService statistics;
    private final Map<String, RouteSortStrategy> strategies;

    public ListActiveRoutesService(RouteReplicaQueries replicas,
                                   ScheduledFlightStatisticsService statistics,
                                   List<RouteSortStrategy> strategyBeans) {
        this.replicas = replicas;
        this.statistics = statistics;
        this.strategies = strategyBeans.stream()
                .collect(Collectors.toMap(RouteSortStrategy::key, s -> s));
    }

    public List<RouteUsage> listActiveRoutes(String sortBy) {
        String key = (sortBy == null || sortBy.isBlank()) ? "popularity" : sortBy.toLowerCase();
        RouteSortStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Invalid sortBy value: '" + sortBy + "'. Allowed values: " + strategies.keySet());
        }

        List<RouteSummary> network = replicas.activeNetwork();
        Map<String, Long> usage = statistics.usageByRoute();
        List<RouteUsage> usages = network.stream()
                .map(route -> new RouteUsage(route, usage.getOrDefault(route.id(), 0L)))
                .toList();
        return strategy.sort(usages);
    }
}
