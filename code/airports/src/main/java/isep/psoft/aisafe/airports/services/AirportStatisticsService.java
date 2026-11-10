package isep.psoft.aisafe.airports.services;

import isep.psoft.aisafe.airports.clients.FlightRoutesClient;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.BusiestAirportDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * US210 — estatística dos aeroportos com mais rotas.
 */
@Service
@RequiredArgsConstructor
public class AirportStatisticsService {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 100;

    private final FlightRoutesClient flightRoutesClient;
    private final AirportReplicaQueries replicas;

    @Transactional(readOnly = true)
    public List<BusiestAirportDTO> busiestAirports(Integer limit) {
        int top = limit == null ? DEFAULT_LIMIT : limit;
        if (top < 1 || top > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT + ".");
        }

        List<Map.Entry<String, Long>> ranking = flightRoutesClient.routeCountsByAirport().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.<String, Long>comparingByKey()))
                .limit(top)
                .toList();

        List<BusiestAirportDTO> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Long> entry : ranking) {
            String iata = entry.getKey().toUpperCase();
            Optional<AirportDTO> airport = replicas.findByIata(iata);
            result.add(new BusiestAirportDTO(
                    rank++,
                    iata,
                    airport.map(AirportDTO::name).orElse(null),
                    airport.map(AirportDTO::city).orElse(null),
                    airport.map(AirportDTO::country).orElse(null),
                    entry.getValue()));
        }
        return result;
    }
}
