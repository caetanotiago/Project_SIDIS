package isep.psoft.aisafe.flightroutes.services.strategy;

import isep.psoft.aisafe.flightroutes.domain.Itinerary;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;

import java.util.List;

/**
 * Strategy (US216): interchangeable algorithm to find alternative itineraries
 * between two airports over the graph of active routes. New algorithms (e.g.
 * shortest distance/time) can be added without changing the service (Forum conv. 013).
 */
public interface RouteSearchStrategy {

    List<Itinerary> search(List<RouteSummary> activeRoutes, String originIata, String destIata);
}
