package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.RouteUsage;

import java.util.List;

/**
 * Strategy (US214): interchangeable criterion to sort active routes.
 * New criteria can be added as new beans without changing the service (Open/Closed).
 */
public interface RouteSortStrategy {

    /** The {@code sortBy} parameter value this strategy handles (e.g. "popularity"). */
    String key();

    List<RouteUsage> sort(List<RouteUsage> routes);
}
