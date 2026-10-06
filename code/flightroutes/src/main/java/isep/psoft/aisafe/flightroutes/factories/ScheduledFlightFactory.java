package isep.psoft.aisafe.flightroutes.factories;

import isep.psoft.aisafe.flightroutes.domain.FlightSchedule;
import isep.psoft.aisafe.flightroutes.domain.RouteReference;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import org.springframework.stereotype.Component;

@Component
public class ScheduledFlightFactory {

    // A rota pode ser local ou de outra réplica: só é guardada a sua referência (+ snapshot).
    public ScheduledFlight create(String aircraftRegistration, RouteSummary route, FlightSchedule schedule) {
        return new ScheduledFlight(aircraftRegistration, RouteReference.of(route), schedule);
    }
}
