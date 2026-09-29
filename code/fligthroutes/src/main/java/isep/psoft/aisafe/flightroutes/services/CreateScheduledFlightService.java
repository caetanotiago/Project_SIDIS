package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.clients.AircraftSnapshot;
import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.FlightSchedule;
import isep.psoft.aisafe.flightroutes.domain.RouteRequirementsNotMetException;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.CreateScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.factories.ScheduledFlightFactory;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US212 - Creates a scheduled flight, enforcing the business rules:
 * range/capacity compliance (422), aircraft availability, airport availability and
 * non-overlapping schedule (409). Missing aircraft/route -> 404.
 * Aircraft e Airports vivem noutros microsserviços e são consultados por REST.
 */
@Service
@RequiredArgsConstructor
public class CreateScheduledFlightService {

    private final AircraftClient aircraftClient;
    private final AirportClient airportClient;
    private final FlightRouteRepository flightRouteRepository;
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final ScheduledFlightFactory factory;

    @Transactional
    public ScheduledFlight create(CreateScheduledFlightDTO dto) {
        // 404 - aircraft must exist (Aircraft Management)
        AircraftSnapshot aircraft = aircraftClient.getAircraft(dto.getAircraftRegistration());

        // 404 - route must exist
        FlightRoute route = flightRouteRepository.findById(dto.getRouteID())
                .orElseThrow(() -> new EntityNotFoundException("Route not found: " + dto.getRouteID()));

        // 422 - aircraft must comply with the route requirements (range & capacity)
        if (!route.getRequirements().isMetBy(aircraft.maximumRange(), aircraft.seatingCapacity())) {
            throw new RouteRequirementsNotMetException(
                    "Aircraft " + dto.getAircraftRegistration()
                            + " does not meet the route requirements (range/capacity).");
        }

        // 409 - aircraft must be available (AircraftStatus states: AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE, INACTIVE)
        if (!aircraft.isAvailable()) {
            throw new IllegalStateException("Aircraft " + dto.getAircraftRegistration()
                    + " is not available (status: " + aircraft.status() + ").");
        }

        // 409 - origin and destination airports must be operational (Airports)
        if (!airportClient.getAirport(route.getOriginIata()).isOperational()) {
            throw new IllegalStateException("Origin airport "
                    + route.getOriginIata() + " is not operational.");
        }
        if (!airportClient.getAirport(route.getDestinationIata()).isOperational()) {
            throw new IllegalStateException("Destination airport "
                    + route.getDestinationIata() + " is not operational.");
        }

        // 409 - the aircraft must not already have a flight at the same date/time
        if (scheduledFlightRepository.existsOverlappingFlight(
                dto.getAircraftRegistration(), dto.getDate(), dto.getTime())) {
            throw new IllegalStateException("Aircraft " + dto.getAircraftRegistration()
                    + " already has a scheduled flight at " + dto.getDate() + " " + dto.getTime() + ".");
        }

        FlightSchedule schedule = new FlightSchedule(dto.getDate(), dto.getTime());
        ScheduledFlight flight = factory.create(aircraft.registrationNumber(), route, schedule);
        return scheduledFlightRepository.save(flight);
    }
}
