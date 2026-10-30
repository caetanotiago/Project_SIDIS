package isep.psoft.aisafe.maintenance.controllers;

import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRequest;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.ScheduleMaintenanceRequest;
import isep.psoft.aisafe.maintenance.services.MaintenanceRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for scheduling, completing, and querying aircraft maintenance.
 *
 * @author Diogo Fernandes - 1240632
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceRecordService maintenanceRecordService;

    @PostMapping
    public ResponseEntity<MaintenanceRecordDTO> start(@Valid @RequestBody ScheduleMaintenanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceRecordService.start(request));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<MaintenanceRecordDTO> complete(
            @PathVariable String id,
            @Valid @RequestBody CompleteMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceRecordService.complete(id, request));
    }

    @GetMapping
    public ResponseEntity<List<MaintenanceRecordDTO>> listAll() {
        return ResponseEntity.ok(maintenanceRecordService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceRecordDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(maintenanceRecordService.getById(id));
    }

    @GetMapping("/aircraft/{registration}")
    public ResponseEntity<List<MaintenanceRecordDTO>> history(@PathVariable String registration) {
        return ResponseEntity.ok(maintenanceRecordService.history(registration));
    }
}
