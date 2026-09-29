package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.psoft.aisafe.flightroutes.services.strategy.RouteSortStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * US214 - Lists active routes sorted by a pluggable criterion (Strategy Pattern).
 */
@Service
public class ListActiveRoutesService {

    private final FlightRouteRepository flightRouteRepository;
    private final Map<String, RouteSortStrategy> strategies;

    public ListActiveRoutesService(FlightRouteRepository flightRouteRepository,
                                   List<RouteSortStrategy> strategyBeans) {
        this.flightRouteRepository = flightRouteRepository;
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
        return strategy.sort(flightRouteRepository.findActiveRoutesWithUsage());
    }
}
