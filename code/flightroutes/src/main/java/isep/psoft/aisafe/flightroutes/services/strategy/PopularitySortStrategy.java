package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

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
                .sorted(Comparator.comparingLong(RouteUsage::usageCount).reversed())
                .toList();
    }
}
