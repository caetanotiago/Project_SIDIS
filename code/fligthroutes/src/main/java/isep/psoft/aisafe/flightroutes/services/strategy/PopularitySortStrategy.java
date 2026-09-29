package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PopularitySortStrategy implements RouteSortStrategy {

    @Override
    public String key() {
        return "popularity";
    }

    @Override
    public List<RouteUsage> sort(List<RouteUsage> routes) {
        // Most used routes first.
        return routes.stream()
                .sorted(Comparator.comparingLong(RouteUsage::getUsageCount).reversed())
                .collect(Collectors.toList());
    }
}
