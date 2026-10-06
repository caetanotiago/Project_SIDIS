package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.clients.AirportSnapshot;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.CreateRouteDTO;
import isep.psoft.aisafe.flightroutes.factories.FlightRouteFactory;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.ROUTE_LIST;
import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.uri;

/**
 * US110 - criar uma rota. A rota fica guardada na réplica que recebeu o pedido (partição por
 * escrita). A unicidade origem→destino é verificada localmente e nas réplicas peer que respondam;
 * com um peer em baixo a criação continua (prioridade à disponibilidade — ver documentação).
 */
@Service
@RequiredArgsConstructor
public class CreateFlightRouteService {

    private final AirportClient airportClient;
    private final FlightRouteRepository routeRepository;
    private final FlightRouteFactory routeFactory;
    private final DistanceCalculatorService distanceCalculator;
    private final PeerClient peerClient;

    @Transactional
    public FlightRoute createRoute(CreateRouteDTO dto) {
        String originIata = dto.getOriginIATA().toUpperCase();
        String destIata = dto.getDestIATA().toUpperCase();

        if (routeRepository.existsByOriginAndDestination(originIata, destIata)
                || !peerClient.collectList(uri("/api/routes/search", "origin", originIata, "dest", destIata),
                        ROUTE_LIST).isEmpty()) {
            throw new IllegalArgumentException("A route between these airports already exists.");
        }

        // Os aeroportos vivem no microsserviço Airports: obtidos por REST.
        AirportSnapshot origin = getAirport(originIata, "Origin");
        AirportSnapshot dest = getAirport(destIata, "Destination");

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
