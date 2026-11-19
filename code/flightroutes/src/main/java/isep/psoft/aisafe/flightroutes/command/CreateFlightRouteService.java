package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.clients.AirportSnapshot;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.CreateRouteDTO;
import isep.psoft.aisafe.flightroutes.factories.FlightRouteFactory;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRouteRepository;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.psoft.aisafe.flightroutes.services.DistanceCalculatorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US110 (command) - create a route. The route is stored in the write model of the replica that received the
 * request, which becomes its owner, and is announced as {@code RouteCreated}.
 *
 * <p>Uniqueness of origin -> destination is checked on the LOCAL read model, which holds the routes of every
 * replica. Inside the eventual-consistency window (a route created on another replica whose event has not arrived
 * yet) a duplicate can still slip through; see docs/2-containers/Containers.md, section 3.6.
 */
@Service
@RequiredArgsConstructor
public class CreateFlightRouteService {

    private final AirportClient airportClient;
    private final FlightRouteRepository routeRepository;
    private final ReplicatedRouteRepository readModel;
    private final FlightRouteFactory routeFactory;
    private final DistanceCalculatorService distanceCalculator;
    private final FlightRoutesEventEmitter events;

    @Transactional
    public FlightRoute createRoute(CreateRouteDTO dto) {
        String originIata = dto.getOriginIATA().toUpperCase();
        String destIata = dto.getDestIATA().toUpperCase();

        if (readModel.existsByOriginAndDestination(originIata, destIata)) {
            throw new IllegalArgumentException("A route between these airports already exists.");
        }

        // B2: the airports live in the Airports microservice (HTTP until it publishes airport events).
        AirportSnapshot origin = getAirport(originIata, "Origin");
        AirportSnapshot dest = getAirport(destIata, "Destination");

        double distance = distanceCalculator.calculateDistance(
                origin.latitude(), origin.longitude(),
                dest.latitude(), dest.longitude());

        FlightRoute newRoute = routeFactory.createRoute(
                origin.iataCode(), dest.iataCode(), distance,
                dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime());

        FlightRoute saved = routeRepository.saveAndFlush(newRoute);
        events.routeCreated(saved);
        return saved;
    }

    // Keeps the 400 of the monolith when the airport does not exist.
    private AirportSnapshot getAirport(String iataCode, String label) {
        try {
            return airportClient.getAirport(iataCode);
        } catch (EntityNotFoundException e) {
            throw new IllegalArgumentException(label + " airport not found: " + iataCode);
        }
    }
}
