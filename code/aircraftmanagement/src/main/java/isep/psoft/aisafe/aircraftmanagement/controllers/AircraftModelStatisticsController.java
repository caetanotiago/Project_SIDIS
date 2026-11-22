package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import isep.psoft.aisafe.aircraftmanagement.query.TopUtilizedModelsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aircraft-models")
@RequiredArgsConstructor
public class AircraftModelStatisticsController {

    private static final int MAX_LIMIT = 20;

    private final TopUtilizedModelsService topUtilizedModelsService;

    /**
     * US204 (query) - most utilized models (by flight minutes), Top 5 by default. Read models only: the fleet
     * ({@code REPLICATED_AIRCRAFT}) and the flight minutes ({@code KNOWN_SCHEDULED_FLIGHT}, Flight Routes events), so
     * it no longer calls Flight Routes (bridge B4b) and is never partial.
     */
    @GetMapping("/top-utilized")
    public List<ModelUtilizationDTO> topUtilized(@RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return topUtilizedModelsService.topUtilized(limit);
    }
}
