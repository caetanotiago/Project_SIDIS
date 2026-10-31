package isep.psoft.aisafe.maintenance.controllers;

import io.swagger.v3.oas.annotations.Operation;
import isep.psoft.aisafe.maintenance.domain.CostGrouping;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO;
import isep.psoft.aisafe.maintenance.services.MaintenanceStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estatísticas e relatórios (US117, US220, US221). Pedido de um cliente: soma de todas as réplicas;
 * pedido de um peer: só o resultado parcial desta réplica.
 */
@RestController
@RequestMapping("/api/maintenance-records")
@RequiredArgsConstructor
public class MaintenanceStatisticsController {

    private final MaintenanceStatisticsService statisticsService;

    @GetMapping("/statistics/hours")
    @Operation(summary = "US117 — Total maintenance hours of the fleet (and per aircraft)")
    public ResponseEntity<FleetMaintenanceHoursDTO> maintenanceHours() {
        return ResponseEntity.ok(statisticsService.maintenanceHours());
    }

    @GetMapping("/reports/costs")
    @Operation(summary = "US220 — Maintenance costs per aircraft (groupBy=AIRCRAFT) or per aircraft model (groupBy=MODEL)")
    public ResponseEntity<CostReportDTO> costs(@RequestParam(required = false, defaultValue = "AIRCRAFT") String groupBy) {
        return ResponseEntity.ok(statisticsService.costs(CostGrouping.parse(groupBy)));
    }

    @GetMapping("/reports/turnaround")
    @Operation(summary = "US221 — Average maintenance turnaround time per aircraft model")
    public ResponseEntity<TurnaroundReportDTO> turnaround() {
        return ResponseEntity.ok(statisticsService.turnaround());
    }

    @GetMapping("/statistics/last-completions")
    @Operation(summary = "Latest completion per aircraft and template (all replicas; used by US222)")
    public ResponseEntity<List<LastCompletionDTO>> lastCompletions() {
        return ResponseEntity.ok(statisticsService.lastCompletions());
    }
}
