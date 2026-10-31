package isep.psoft.aisafe.maintenance.controllers;

import io.swagger.v3.oas.annotations.Operation;
import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.dto.CategorizeRecordDTO;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import isep.psoft.aisafe.maintenance.services.CategorizeMaintenanceRecordService;
import isep.psoft.aisafe.maintenance.services.CompleteMaintenanceRecordService;
import isep.psoft.aisafe.maintenance.services.CreateMaintenanceRecordService;
import isep.psoft.aisafe.maintenance.services.RecordFilter;
import isep.psoft.aisafe.maintenance.services.ViewMaintenanceRecordsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** /api/maintenance-records — US115 (registos), US116, US119, US217, US218, US219. */
@RestController
@RequestMapping("/api/maintenance-records")
@RequiredArgsConstructor
public class MaintenanceRecordController {

    /** Header da resposta da US119: a aeronave voltou (true) ou não (false) a AVAILABLE. */
    public static final String AIRCRAFT_RELEASED = "X-Aircraft-Released";

    private final CreateMaintenanceRecordService createService;
    private final CompleteMaintenanceRecordService completeService;
    private final CategorizeMaintenanceRecordService categorizeService;
    private final ViewMaintenanceRecordsService viewService;
    private final MaintenanceRecordAssembler assembler;

    @PostMapping
    @Operation(summary = "US115 — Create a maintenance record (aircraft set UNDER_MAINTENANCE)")
    public ResponseEntity<MaintenanceRecordDTO> create(@Valid @RequestBody CreateMaintenanceRecordDTO dto) {
        MaintenanceRecordDTO created = assembler.toDTO(createService.create(dto));
        return ResponseEntity.created(URI.create(created.getRequiredLink("self").getHref())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a maintenance record by id (any replica)")
    public ResponseEntity<MaintenanceRecordDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(viewService.getById(id));
    }

    @GetMapping("/aircraft/{registration}")
    @Operation(summary = "US116 — Maintenance records of an aircraft (all replicas, paginated)")
    public ResponseEntity<PageDTO<MaintenanceRecordDTO>> byAircraft(
            @PathVariable String registration,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageDTO.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(viewService.byAircraft(registration, page, size));
    }

    @GetMapping
    @Operation(summary = "US218 — Search records by aircraft, component, status and/or start-date range (paginated)")
    public ResponseEntity<PageDTO<MaintenanceRecordDTO>> search(
            @RequestParam(required = false) String aircraft,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageDTO.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(viewService.search(RecordFilter.of(aircraft, component, status, from, to), page, size));
    }

    @GetMapping("/ongoing")
    @Operation(summary = "US219 — Ongoing maintenance activities across the fleet (paginated)")
    public ResponseEntity<PageDTO<MaintenanceRecordDTO>> ongoing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageDTO.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(viewService.ongoing(page, size));
    }

    // Pesquisa não paginada: usada pelas réplicas peer para agregar US116/US218/US219 e pela US119.
    @GetMapping("/search")
    @Operation(summary = "Records matching the filters, not paginated (all replicas; used for peer aggregation)")
    public ResponseEntity<List<MaintenanceRecordDTO>> searchAll(
            @RequestParam(required = false) String aircraft,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(viewService.searchAll(RecordFilter.of(aircraft, component, status, from, to)));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "US119 — Complete a maintenance record (executed by the owner replica)")
    public ResponseEntity<MaintenanceRecordDTO> complete(@PathVariable String id,
                                                         @Valid @RequestBody CompleteMaintenanceRecordDTO dto) {
        CompleteMaintenanceRecordService.Result result = completeService.complete(id, dto);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.aircraftReleased() != null) {
            response.header(AIRCRAFT_RELEASED, result.aircraftReleased().toString());
        }
        return response.body(result.record());
    }

    @PatchMapping("/{id}/component")
    @Operation(summary = "US217 — Categorise a maintenance record by component (executed by the owner replica)")
    public ResponseEntity<MaintenanceRecordDTO> categorize(@PathVariable String id,
                                                           @Valid @RequestBody CategorizeRecordDTO dto) {
        return ResponseEntity.ok(categorizeService.categorize(id, dto));
    }
}
