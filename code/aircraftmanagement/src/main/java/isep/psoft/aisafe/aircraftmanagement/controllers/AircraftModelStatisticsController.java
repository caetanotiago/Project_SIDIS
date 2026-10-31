package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import isep.psoft.aisafe.aircraftmanagement.services.TopUtilizedModelsService;
import isep.sidis.common.http.AisafeHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    /** US204 – modelos mais utilizados (por minutos de voo). Por omissão o Top 5. */
    @GetMapping("/top-utilized")
    public ResponseEntity<List<ModelUtilizationDTO>> topUtilized(@RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        TopUtilizedModelsService.Result result = topUtilizedModelsService.topUtilized(limit);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.partial()) {
            response.header(AisafeHeaders.PARTIAL_RESPONSE, "true");
        }
        return response.body(result.rows());
    }
}
