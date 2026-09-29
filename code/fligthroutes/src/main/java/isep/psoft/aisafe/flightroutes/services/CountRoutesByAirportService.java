package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * US210 - Nº de rotas por aeroporto (origem + destino).
 * Exposto para o microsserviço Airports, que calcula os aeroportos mais movimentados
 * (antes usava diretamente o FlightRouteRepository).
 */
@Service
@RequiredArgsConstructor
public class CountRoutesByAirportService {

    private final FlightRouteRepository flightRouteRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> countRoutesByAirport() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : flightRouteRepository.countByOrigin()) {
            counts.merge((String) row[0], (Long) row[1], Long::sum);
        }
        for (Object[] row : flightRouteRepository.countByDestination()) {
            counts.merge((String) row[0], (Long) row[1], Long::sum);
        }
        return counts;
    }
}
