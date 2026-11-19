package isep.psoft.aisafe.flightroutes.controllers;

import isep.psoft.aisafe.flightroutes.assemblers.ScheduledFlightAssembler;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO;
import isep.psoft.aisafe.flightroutes.dto.CreateScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.query.CalculateOperationalHoursService;
import isep.psoft.aisafe.flightroutes.command.CreateScheduledFlightService;
import isep.psoft.aisafe.flightroutes.query.ScheduledFlightStatisticsService;
import isep.psoft.aisafe.flightroutes.query.ViewScheduledFlightsByAircraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Scheduled flights (WP#3B). POST is a command on this replica's write model; every GET is a query on this
 * replica's flight read model, which holds the flights of every replica (see FlightRouteController).
 */
@RestController
@RequestMapping("/api/scheduled-flights")
@RequiredArgsConstructor
@Tag(name = "Scheduled Flights", description = "Flight operations — create and view scheduled flights (WP#3B)")
public class ScheduledFlightController {

    private final CreateScheduledFlightService createService;
    private final ViewScheduledFlightsByAircraftService viewService;
    private final CalculateOperationalHoursService operationalHoursService;
    private final ScheduledFlightStatisticsService statisticsService;
    private final ScheduledFlightAssembler assembler;

    // US212 (command): create a scheduled flight (stored in, and owned by, this replica)
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

    // US206 (query, B4a provider): operational hours per aircraft
    @GetMapping("/operational-hours")
    @Operation(summary = "US206 — Total operational hours per aircraft, from its scheduled flights")
    public ResponseEntity<Page<AircraftOperationalHoursDTO>> getOperationalHours(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(operationalHoursService.calculateOperationalHours(pageable));
    }

    // (query) Non-paginated search of the flights of an aircraft, optionally at a date/time.
    @GetMapping("/search")
    @Operation(summary = "Scheduled flights of an aircraft, optionally at a given date/time (all replicas)")
    public ResponseEntity<List<ScheduledFlightDTO>> searchScheduledFlights(
            @RequestParam String aircraft,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        return ResponseEntity.ok(viewService.search(aircraft, date, time));
    }

    // US214 (query, popularity): number of flights per route
    @GetMapping("/statistics/usage-by-route")
    @Operation(summary = "Number of scheduled flights per route id (all replicas)")
    public ResponseEntity<Map<String, Long>> usageByRoute() {
        return ResponseEntity.ok(statisticsService.usageByRoute());
    }

    // US206 (query, B4b provider): flight minutes per aircraft (called by Aircraft Management)
    @GetMapping("/statistics/minutes-by-aircraft")
    @Operation(summary = "Scheduled flight minutes per aircraft registration (all replicas)")
    public ResponseEntity<Map<String, Long>> minutesByAircraft() {
        return ResponseEntity.ok(statisticsService.minutesByAircraft());
    }

    // (query) Details (target of the HATEOAS self link), from the local read model
    @GetMapping("/{id}")
    @Operation(summary = "Get the details of a scheduled flight by its id")
    public ResponseEntity<ScheduledFlightDTO> getScheduledFlightById(@PathVariable String id) {
        return ResponseEntity.ok(viewService.getById(id));
    }

    // US213 (query): scheduled flights of an aircraft (paginated)
    @GetMapping
    @Operation(summary = "US213 — View all scheduled flights for a specific aircraft (paginated, all replicas)")
    public ResponseEntity<PagedModel<ScheduledFlightDTO>> getScheduledFlightsByAircraft(
            @RequestParam String aircraft,
            @ParameterObject Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<ScheduledFlightDTO> pagedAssembler) {
        Page<ScheduledFlightDTO> page = viewService.viewByAircraft(aircraft, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, dto -> dto));
    }
}
