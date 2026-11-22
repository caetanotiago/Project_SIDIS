package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * US204 (query): minutes per registration ({@code KNOWN_SCHEDULED_FLIGHT}) -> map each registration to its model
 * ({@code REPLICATED_AIRCRAFT}) -> sum per model -> sort descending -> first N. "Most utilized" = flight time
 * (decision Q5). Models without flight minutes are not ranked.
 */
@Service
@RequiredArgsConstructor
public class TopUtilizedModelsService {

    private final SearchAircraftsService searchService;
    private final FlightMinutesService flightMinutes;

    @Transactional(readOnly = true)
    public List<ModelUtilizationDTO> topUtilized(int limit) {
        Map<String, Long> minutes = flightMinutes.minutesByAircraft();

        Map<String, Long> minutesByModel = new HashMap<>();
        Map<String, Integer> countByModel = new HashMap<>();
        for (AircraftSummary a : searchService.fleet()) {
            minutesByModel.merge(a.modelName(), minutes.getOrDefault(a.registrationNumber(), 0L), Long::sum);
            countByModel.merge(a.modelName(), 1, Integer::sum);
        }

        return minutesByModel.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> ModelUtilizationDTO.of(e.getKey(), e.getValue(), countByModel.get(e.getKey())))
                .sorted((x, y) -> {
                    int c = Long.compare(y.totalMinutes(), x.totalMinutes());
                    return c != 0 ? c : x.modelName().compareTo(y.modelName());
                })
                .limit(limit)
                .toList();
    }
}
