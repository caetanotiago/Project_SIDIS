package isep.psoft.aisafe.flightroutes.factories;

import isep.psoft.aisafe.flightroutes.domain.*;
import org.springframework.stereotype.Component;

@Component
public class FlightRouteFactory {

    public FlightRoute createRoute(String originIata, String destinationIata, double distanceVal,
                                   double minRange, int minCapacity, int estTimeMinutes) {

        // Instancia os Value Objects
        RouteDistance distance = new RouteDistance(distanceVal);
        RouteRequirements requirements = new RouteRequirements(minRange, minCapacity);
        EstimatedFlightTime time = new EstimatedFlightTime(estTimeMinutes);

        // Retorna a entidade montada
        return new FlightRoute(originIata, destinationIata, distance, requirements, time);
    }
}
