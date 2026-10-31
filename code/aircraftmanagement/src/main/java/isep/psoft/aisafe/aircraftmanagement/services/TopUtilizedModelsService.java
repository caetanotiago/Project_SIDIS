package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.MinutesByAircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * US204 (receita do contrato, ponte B4b): minutos por matrícula (Flight Routes) → mapear cada matrícula
 * para o seu modelo (frota de todas as réplicas) → somar por modelo → ordenar desc → primeiros N.
 * "Mais utilizado" = tempo de voo (decisão Q5). Modelos sem minutos de voo não entram no ranking.
 */
@Service
@RequiredArgsConstructor
public class TopUtilizedModelsService {

    private final SearchAircraftsService searchService;
    private final FlightRoutesClient flightRoutesClient;

    public record Result(List<ModelUtilizationDTO> rows, boolean partial) { }

    public Result topUtilized(int limit) {
        List<AircraftSummary> fleet = searchService.search(null, null, null);
        MinutesByAircraft usage = flightRoutesClient.minutesByAircraft();

        Map<String, Long> minutesByModel = new HashMap<>();
        Map<String, Integer> countByModel = new HashMap<>();
        for (AircraftSummary a : fleet) {
            minutesByModel.merge(a.modelName(), usage.minutes().getOrDefault(a.registrationNumber(), 0L), Long::sum);
            countByModel.merge(a.modelName(), 1, Integer::sum);
        }

        List<ModelUtilizationDTO> rows = minutesByModel.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> ModelUtilizationDTO.of(e.getKey(), e.getValue(), countByModel.get(e.getKey())))
                .sorted((x, y) -> {
                    int c = Long.compare(y.totalMinutes(), x.totalMinutes());
                    return c != 0 ? c : x.modelName().compareTo(y.modelName());
                })
                .limit(limit)
                .toList();
        return new Result(rows, usage.partial());
    }
}
