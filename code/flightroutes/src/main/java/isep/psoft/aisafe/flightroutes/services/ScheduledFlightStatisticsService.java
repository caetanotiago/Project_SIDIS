package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import isep.sidis.common.peers.PeerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregados dos voos agendados de TODAS as réplicas. Cada voo está guardado numa única réplica,
 * por isso as contagens/somas parciais de cada réplica podem ser somadas.
 * <ul>
 *   <li>{@link #usageByRoute()} — nº de voos por rota (popularidade, US214);</li>
 *   <li>{@link #minutesByAircraft()} — minutos de voo por aeronave (horas operacionais, US206).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduledFlightStatisticsService {

    private static final ParameterizedTypeReference<Map<String, Long>> COUNTS = new ParameterizedTypeReference<>() { };

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final PeerClient peerClient;

    @Transactional(readOnly = true)
    public Map<String, Long> usageByRoute() {
        return sumWithPeers(scheduledFlightRepository.countByRoute(),
                "/api/scheduled-flights/statistics/usage-by-route");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> minutesByAircraft() {
        return sumWithPeers(scheduledFlightRepository.sumMinutesByAircraft(),
                "/api/scheduled-flights/statistics/minutes-by-aircraft");
    }

    private Map<String, Long> sumWithPeers(List<Object[]> localRows, String peerUri) {
        Map<String, Long> totals = new HashMap<>();
        for (Object[] row : localRows) {
            totals.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        peerClient.collect(peerUri, COUNTS).responses()
                .forEach(peer -> peer.forEach((key, n) -> totals.merge(key, n, Long::sum)));
        return totals;
    }
}
