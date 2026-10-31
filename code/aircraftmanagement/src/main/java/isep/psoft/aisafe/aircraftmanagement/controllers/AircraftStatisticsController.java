package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.CompatibleRoutes;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftOperationalHoursDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CompatibleRouteDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.PageDTO;
import isep.psoft.aisafe.aircraftmanagement.services.CalculateOperationalHoursService;
import isep.psoft.aisafe.aircraftmanagement.services.FindCompatibleRoutesService;
import isep.sidis.common.http.AisafeHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consultas sobre aeronaves que combinam dados do Flight Routes (US203 e US206). */
@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftStatisticsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CalculateOperationalHoursService operationalHoursService;
    private final FindCompatibleRoutesService compatibleRoutesService;

    /**
     * US206 – horas operacionais de cada aeronave da frota (paginado).
     * Se o Flight Routes devolver dados incompletos, a resposta leva X-Partial-Response: true.
     */
    @GetMapping("/operational-hours")
    public ResponseEntity<PageDTO<AircraftOperationalHoursDTO>> operationalHours(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        CalculateOperationalHoursService.Result result = operationalHoursService.calculate();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.partial()) {
            response.header(AisafeHeaders.PARTIAL_RESPONSE, "true");
        }
        return response.body(PageDTO.of(result.rows(), page, size));
    }

    /** US203 – rotas ativas compatíveis com o alcance do modelo e a capacidade da aeronave. */
    @GetMapping("/{registrationNumber}/compatible-routes")
    public ResponseEntity<List<CompatibleRouteDTO>> compatibleRoutes(@PathVariable String registrationNumber) {
        CompatibleRoutes result = compatibleRoutesService.findFor(registrationNumber);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.partial()) {
            response.header(AisafeHeaders.PARTIAL_RESPONSE, "true");
        }
        return response.body(result.routes());
    }
}
