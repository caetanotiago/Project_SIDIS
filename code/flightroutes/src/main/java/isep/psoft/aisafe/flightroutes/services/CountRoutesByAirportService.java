package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.sidis.common.peers.PeerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * US210 - Nº de rotas por aeroporto (origem + destino), somado em todas as réplicas.
 * Exposto para o microsserviço Airports, que calcula os aeroportos mais movimentados.
 * Cada rota está guardada numa só réplica, por isso as contagens parciais podem ser somadas.
 */
@Service
@RequiredArgsConstructor
public class CountRoutesByAirportService {

    private static final ParameterizedTypeReference<Map<String, Long>> COUNTS = new ParameterizedTypeReference<>() { };

    private final FlightRouteRepository flightRouteRepository;
    private final PeerClient peerClient;

    @Transactional(readOnly = true)
    public Map<String, Long> countRoutesByAirport() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : flightRouteRepository.countByOrigin()) {
            counts.merge((String) row[0], (Long) row[1], Long::sum);
        }
        for (Object[] row : flightRouteRepository.countByDestination()) {
            counts.merge((String) row[0], (Long) row[1], Long::sum);
        }
        peerClient.collect("/api/routes/statistics/count-by-airport", COUNTS).responses()
                .forEach(peerCounts -> peerCounts.forEach((iata, n) -> counts.merge(iata, n, Long::sum)));
        return counts;
    }
}
