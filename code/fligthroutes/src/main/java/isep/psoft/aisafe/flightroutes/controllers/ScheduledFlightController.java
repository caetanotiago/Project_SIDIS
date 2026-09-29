package isep.psoft.aisafe.flightroutes.controllers;

import isep.psoft.aisafe.flightroutes.assemblers.ScheduledFlightAssembler;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.CreateScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.services.CreateScheduledFlightService;
import isep.psoft.aisafe.flightroutes.services.ViewScheduledFlightsByAircraftService;
import isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO;
import isep.psoft.aisafe.flightroutes.services.CalculateOperationalHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/scheduled-flights")
@RequiredArgsConstructor
@Tag(name = "Scheduled Flights", description = "Flight operations — create and view scheduled flights (WP#3B)")
public class ScheduledFlightController {

    private final CreateScheduledFlightService createService;
    private final ViewScheduledFlightsByAircraftService viewService;
    private final CalculateOperationalHoursService operationalHoursService;
    private final ScheduledFlightAssembler assembler;

    // US212 - Criar voo agendado
    @PostMapping
    @Operation(summary = "US212 — Assign an aircraft to a route for a date/time (create a scheduled flight)")
    public ResponseEntity<ScheduledFlightDTO> createScheduledFlight(
            @Valid @RequestBody CreateScheduledFlightDTO dto) {
        ScheduledFlight flight = createService.create(dto);
        ScheduledFlightDTO body = assembler.toModel(flight);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(flight.getId())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    // US206 - Horas operacionais por aeronave (chamado pelo microsserviço Aircraft Management)
    @GetMapping("/operational-hours")
    @Operation(summary = "US206 — Total operational hours per aircraft, from its scheduled flights")
    public ResponseEntity<Page<AircraftOperationalHoursDTO>> getOperationalHours(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(operationalHoursService.calculateOperationalHours(pageable));
    }

    // Detalhe (suporta self-link do HATEOAS)
    @GetMapping("/{id}")
    @Operation(summary = "Get the details of a scheduled flight by its id")
    public ResponseEntity<ScheduledFlightDTO> getScheduledFlightById(@PathVariable String id) {
        return ResponseEntity.ok(assembler.toModel(viewService.getById(id)));
    }

    // US213 - Ver voos agendados de uma aeronave (paginado)
    @GetMapping
    @Operation(summary = "US213 — View all scheduled flights for a specific aircraft (paginated)")
    public ResponseEntity<PagedModel<ScheduledFlightDTO>> getScheduledFlightsByAircraft(
            @RequestParam String aircraft,
            @ParameterObject Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<ScheduledFlight> pagedAssembler) {
        Page<ScheduledFlight> page = viewService.viewByAircraft(aircraft, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }
}
