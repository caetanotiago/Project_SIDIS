package isep.psoft.aisafe.maintenance.controllers;

import io.swagger.v3.oas.annotations.Operation;
import isep.psoft.aisafe.maintenance.dto.MaintenanceAlertDTO;
import isep.psoft.aisafe.maintenance.services.MaintenanceAlertService;
import isep.sidis.common.http.AisafeHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/maintenance-alerts — US222 (verificação disparada manualmente pelo ATCC). */
@RestController
@RequestMapping("/api/maintenance-alerts")
@RequiredArgsConstructor
public class MaintenanceAlertController {

    private final MaintenanceAlertService alertService;

    @GetMapping
    @Operation(summary = "US222 — Aircraft due for scheduled maintenance (flight hours or calendar days)")
    public ResponseEntity<List<MaintenanceAlertDTO>> alerts() {
        MaintenanceAlertService.AlertResult result = alertService.checkFleet();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.partial()) {
            response.header(AisafeHeaders.PARTIAL_RESPONSE, "true");
        }
        return response.body(result.alerts());
    }
}
