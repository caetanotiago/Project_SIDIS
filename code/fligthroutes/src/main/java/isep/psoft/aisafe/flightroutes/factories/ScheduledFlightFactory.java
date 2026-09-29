package isep.psoft.aisafe.flightroutes.factories;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.FlightSchedule;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import org.springframework.stereotype.Component;

@Component
public class ScheduledFlightFactory {

    public ScheduledFlight create(String aircraftRegistration, FlightRoute route, FlightSchedule schedule) {
        return new ScheduledFlight(aircraftRegistration, route, schedule);
    }
}
