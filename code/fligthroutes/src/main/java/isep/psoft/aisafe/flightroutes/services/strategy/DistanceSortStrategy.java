package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DistanceSortStrategy implements RouteSortStrategy {

    @Override
    public String key() {
        return "distance";
    }

    @Override
    public List<RouteUsage> sort(List<RouteUsage> routes) {
        // Longest routes first.
        return routes.stream()
                .sorted(Comparator.comparingDouble(
                        (RouteUsage ru) -> ru.getRoute().getDistance().getDistance()).reversed())
                .collect(Collectors.toList());
    }
}
