package isep.psoft.aisafe.maintenance.controllers;

import io.swagger.v3.oas.annotations.Operation;
import isep.psoft.aisafe.maintenance.assemblers.MaintenanceTemplateAssembler;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.services.CreateMaintenanceTemplateService;
import isep.psoft.aisafe.maintenance.services.ViewMaintenanceTemplatesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** /api/maintenance-templates — US115 (templates). */
@RestController
@RequestMapping("/api/maintenance-templates")
@RequiredArgsConstructor
public class MaintenanceTemplateController {

    private final CreateMaintenanceTemplateService createService;
    private final ViewMaintenanceTemplatesService viewService;
    private final MaintenanceTemplateAssembler assembler;

    @PostMapping
    @Operation(summary = "US115 — Create a maintenance template (name, type, applicable models, checklist, interval)")
    public ResponseEntity<MaintenanceTemplateDTO> create(@Valid @RequestBody CreateMaintenanceTemplateDTO dto) {
        MaintenanceTemplateDTO created = assembler.toDTO(createService.create(dto));
        return ResponseEntity.created(URI.create(created.getRequiredLink("self").getHref())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a maintenance template by id (any replica)")
    public ResponseEntity<MaintenanceTemplateDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(viewService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List maintenance templates of all replicas, optionally by aircraft model, type or name")
    public ResponseEntity<List<MaintenanceTemplateDTO>> list(@RequestParam(required = false) String model,
                                                             @RequestParam(required = false) String type,
                                                             @RequestParam(required = false) String name) {
        return ResponseEntity.ok(viewService.list(model, type, name));
    }
}
