package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.clients.AirportSnapshot;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.CreateRouteDTO;
import isep.psoft.aisafe.flightroutes.factories.FlightRouteFactory;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateFlightRouteService {

    private final AirportClient airportClient;
    private final FlightRouteRepository routeRepository;
    private final FlightRouteFactory routeFactory;
    private final DistanceCalculatorService distanceCalculator;

    @Transactional
    public FlightRoute createRoute(CreateRouteDTO dto) {

        if (routeRepository.existsByOriginAndDestination(dto.getOriginIATA(), dto.getDestIATA())) {
            throw new IllegalArgumentException("A route between these airports already exists.");
        }

        // Os aeroportos vivem no microsserviço Airports: obtidos por REST.
        AirportSnapshot origin = getAirport(dto.getOriginIATA(), "Origin");
        AirportSnapshot dest = getAirport(dto.getDestIATA(), "Destination");

        double distance = distanceCalculator.calculateDistance(
                origin.latitude(), origin.longitude(),
                dest.latitude(), dest.longitude()
        );

        FlightRoute newRoute = routeFactory.createRoute(
                origin.iataCode(), dest.iataCode(), distance,
                dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime()
        );

        return routeRepository.save(newRoute);
    }

    // Mantém o 400 do monólito quando o aeroporto não existe.
    private AirportSnapshot getAirport(String iataCode, String label) {
        try {
            return airportClient.getAirport(iataCode);
        } catch (EntityNotFoundException e) {
            throw new IllegalArgumentException(label + " airport not found: " + iataCode);
        }
    }
}
