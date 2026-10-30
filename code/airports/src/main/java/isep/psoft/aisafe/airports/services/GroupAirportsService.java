package isep.psoft.aisafe.airports.services;

import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.AirportGroupDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US211 — aeroportos agrupados por região ou por país.
 *
 * <p>Parte da lista de TODOS os aeroportos de TODAS as réplicas (a mesma agregação da US108, sem
 * filtros) e agrupa-a aqui. Agrupar só depois de juntar os dados é obrigatório: cada réplica tem só
 * uma parte dos aeroportos, por isso um grupo pode ter aeroportos guardados em réplicas diferentes.
 * Aeroportos sem região ficam no grupo "Unspecified". Grupos por ordem alfabética.
 */
@Service
@RequiredArgsConstructor
public class GroupAirportsService {

    static final String UNSPECIFIED = "Unspecified";

    private final SearchAirportsService searchService;

    @Transactional(readOnly = true)
    public List<AirportGroupDTO> groupBy(String by) {
        Function<AirportDTO, String> key = switch (by == null ? "country" : by.trim().toLowerCase(Locale.ROOT)) {
            case "country" -> AirportDTO::country;
            case "region" -> AirportDTO::region;
            default -> throw new IllegalArgumentException("Invalid grouping: " + by + ". Allowed: country, region.");
        };

        Map<String, List<AirportDTO>> groups = searchService.search(null, null, null).stream()
                .collect(Collectors.groupingBy(
                        a -> {
                            String value = key.apply(a);
                            return value == null || value.isBlank() ? UNSPECIFIED : value;
                        },
                        TreeMap::new,
                        Collectors.toList()));

        return groups.entrySet().stream()
                .map(e -> new AirportGroupDTO(e.getKey(), e.getValue().size(), e.getValue()))
                .toList();
    }
}
