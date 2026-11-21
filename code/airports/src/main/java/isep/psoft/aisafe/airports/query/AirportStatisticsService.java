package isep.psoft.aisafe.airports.query;

import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.BusiestAirportDTO;
import isep.psoft.aisafe.airports.query.external.RouteCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * US210 — estatística dos aeroportos com mais rotas. Lado de leitura, SEM chamadas HTTP: as contagens vêm do read
 * model {@code KNOWN_ROUTE} (eventos de rotas do Flight Routes) e os detalhes dos aeroportos do read model
 * {@code REPLICATED_AIRPORT}. Responde mesmo com o Flight Routes em baixo (com as rotas que já conhece).
 */
@Service
@RequiredArgsConstructor
public class AirportStatisticsService {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 100;

    private final RouteCountProjection routes;
    private final SearchAirportsService airports;

    @Transactional(readOnly = true)
    public List<BusiestAirportDTO> busiestAirports(Integer limit) {
        int top = limit == null ? DEFAULT_LIMIT : limit;
        if (top < 1 || top > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT + ".");
        }

        List<Map.Entry<String, Long>> ranking = routes.routeCountsByAirport().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.<String, Long>comparingByKey()))
                .limit(top)
                .toList();

        List<BusiestAirportDTO> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Long> entry : ranking) {
            String iata = entry.getKey().toUpperCase();
            Optional<AirportDTO> airport = airports.findByIata(iata);
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
