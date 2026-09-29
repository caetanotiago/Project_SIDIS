package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// US209 - Visualizar todas as rotas que partem de ou chegam a um aeroporto específico.
// Vive no módulo flightroutes (o recurso devolvido é FlightRoute); consulta o
// microsserviço Airports apenas para validar a existência do aeroporto.
@Service
@RequiredArgsConstructor
public class ViewRoutesByAirportService {

    private final AirportClient airportClient;
    private final FlightRouteRepository flightRouteRepository;

    @Transactional(readOnly = true)
    public List<FlightRoute> findRoutesByAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        if (!airportClient.exists(code))
            throw new EntityNotFoundException("Airport not found: " + iataCode);

        return flightRouteRepository.findByOriginOrDestination(code);
    }
}
