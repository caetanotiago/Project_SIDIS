package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.clients.AircraftSnapshot;
import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.FlightSchedule;
import isep.psoft.aisafe.flightroutes.domain.RouteRequirementsNotMetException;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.CreateScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.factories.ScheduledFlightFactory;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRoute;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRouteRepository;
import isep.psoft.aisafe.flightroutes.query.ReplicatedScheduledFlightRepository;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US212 (command) - create a scheduled flight, enforcing the business rules: route must exist (404) and be active
 * (409), range/capacity compliance (422), aircraft availability, airport availability and non-overlapping schedule
 * (409). The flight is stored in the replica that received the request (its owner) and announced as
 * {@code ScheduledFlightCreated}.
 *
 * <p>The route and the overlap check come from this replica's read models (routes and flights of every replica),
 * so no peer is called. The aircraft (B1) and the airports (B2) are still read over HTTP until Aircraft Management
 * and Airports publish events.
 */
@Service
@RequiredArgsConstructor
public class CreateScheduledFlightService {

    private final AircraftClient aircraftClient;
    private final AirportClient airportClient;
    private final ReplicatedRouteRepository routes;
    private final ReplicatedScheduledFlightRepository flights;
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final ScheduledFlightFactory factory;
    private final FlightRoutesEventEmitter events;

    @Transactional
    public ScheduledFlight create(CreateScheduledFlightDTO dto) {
        // 404 - aircraft must exist (Aircraft Management)
        AircraftSnapshot aircraft = aircraftClient.getAircraft(dto.getAircraftRegistration());

        // 404 - route must exist (read model: any replica)
        RouteSummary route = routes.findById(dto.getRouteID()).map(ReplicatedRoute::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Route not found: " + dto.getRouteID()));

        // 409 - only active routes can be operated
        if (!route.isActive()) {
            throw new IllegalStateException("Route " + route.id() + " is not active (status: " + route.status() + ").");
        }

        // 422 - aircraft must comply with the route requirements (range & capacity)
        if (!route.isMetBy(aircraft.maximumRange(), aircraft.seatingCapacity())) {
            throw new RouteRequirementsNotMetException(
                    "Aircraft " + dto.getAircraftRegistration()
                            + " does not meet the route requirements (range/capacity).");
        }

        // 409 - aircraft must be available (AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE, INACTIVE)
        if (!aircraft.isAvailable()) {
            throw new IllegalStateException("Aircraft " + dto.getAircraftRegistration()
                    + " is not available (status: " + aircraft.status() + ").");
        }

        // 409 - origin and destination airports must be operational (Airports)
        if (!airportClient.getAirport(route.originIata()).isOperational()) {
            throw new IllegalStateException("Origin airport " + route.originIata() + " is not operational.");
        }
        if (!airportClient.getAirport(route.destinationIata()).isOperational()) {
            throw new IllegalStateException("Destination airport " + route.destinationIata() + " is not operational.");
        }

        // 409 - the aircraft must not already have a flight at the same date/time (read model: any replica)
        if (!flights.findConflicting(aircraft.registrationNumber(), dto.getDate(), dto.getTime()).isEmpty()) {
            throw new IllegalStateException("Aircraft " + dto.getAircraftRegistration()
                    + " already has a scheduled flight at " + dto.getDate() + " " + dto.getTime() + ".");
        }

        FlightSchedule schedule = new FlightSchedule(dto.getDate(), dto.getTime());
        ScheduledFlight saved = scheduledFlightRepository.saveAndFlush(
                factory.create(aircraft.registrationNumber(), route, schedule));
        events.scheduledFlightCreated(saved);
        return saved;
    }
}
