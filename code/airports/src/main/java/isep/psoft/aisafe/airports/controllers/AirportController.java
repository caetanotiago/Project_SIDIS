package isep.psoft.aisafe.airports.controllers;

import isep.psoft.aisafe.airports.assemblers.AirportAssembler;
import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.dto.AddCertificationDTO;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.AirportGroupDTO;
import isep.psoft.aisafe.airports.dto.BusiestAirportDTO;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.dto.UpdateAirportDetailsDTO;
import isep.psoft.aisafe.airports.dto.UpdateAirportStatusDTO;
import isep.psoft.aisafe.airports.command.RegisterAirportService;
import isep.psoft.aisafe.airports.query.AirportStatisticsService;
import isep.psoft.aisafe.airports.query.GroupAirportsService;
import isep.psoft.aisafe.airports.query.SearchAirportsService;
import isep.psoft.aisafe.airports.command.UpdateAirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/airports")
@RequiredArgsConstructor
@Tag(name = "Airports", description = "Airport management (WP#2A & WP#2B)")
public class AirportController {
    private final RegisterAirportService registerService;
    private final SearchAirportsService searchService;
    private final UpdateAirportService updateService;
    private final AirportStatisticsService statisticsService;
    private final GroupAirportsService groupService;
    private final AirportAssembler assembler;

    @PostMapping
    @Operation(summary = "US106/US207 — Register an airport with runways and optional facilities")
    public ResponseEntity<AirportDTO> register(@Valid @RequestBody CreateAirportDTO dto) {
        Airport airport = registerService.register(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{iata}").buildAndExpand(airport.getIataCode()).toUri();
        return ResponseEntity.created(location).body(assembler.toDTO(airport));
    }

    @GetMapping("/{iataCode}")
    @Operation(summary = "US107 — Airport details by IATA code (answered from this replica's read model)")
    public ResponseEntity<AirportDTO> getByIata(@PathVariable String iataCode) {
        return ResponseEntity.ok(searchService.getByIata(iataCode));
    }

    @GetMapping
    @Operation(summary = "US108 — Search airports by city, country or name (read model with the airports of all replicas)")
    public ResponseEntity<List<AirportDTO>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(searchService.search(city, country, name));
    }

    @GetMapping("/statistics/busiest")
    @Operation(summary = "US210 — Busiest airports by number of routes (route counts from Flight Routes)")
    public ResponseEntity<List<BusiestAirportDTO>> busiestAirports(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(statisticsService.busiestAirports(limit));
    }

    @GetMapping("/grouped")
    @Operation(summary = "US211 — Airports grouped by region or country (by=country|region, default country)")
    public ResponseEntity<List<AirportGroupDTO>> groupAirports(
            @RequestParam(defaultValue = "country") String by) {
        return ResponseEntity.ok(groupService.groupBy(by));
    }

    @PostMapping("/{iataCode}/certifications")
    @Operation(summary = "US106a — Certify an aircraft model to fly to/from the airport (forwarded to the owner replica)")
    public ResponseEntity<AirportDTO> addCertification(
            @PathVariable String iataCode,
            @Valid @RequestBody AddCertificationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(updateService.addCertification(iataCode, dto));
    }

    @PatchMapping("/{iataCode}/status")
    @Operation(summary = "US109 — Update the operational status (OPERATIONAL, CLOSED, UNDER_MAINTENANCE)")
    public ResponseEntity<AirportDTO> changeStatus(
            @PathVariable String iataCode,
            @Valid @RequestBody UpdateAirportStatusDTO dto) {
        return ResponseEntity.ok(updateService.changeStatus(iataCode, dto));
    }

    @PatchMapping("/{iataCode}")
    @Operation(summary = "US208 — Update airport details: operational hours, contacts and facilities")
    public ResponseEntity<AirportDTO> updateDetails(
            @PathVariable String iataCode,
            @Valid @RequestBody UpdateAirportDetailsDTO dto) {
        return ResponseEntity.ok(updateService.updateDetails(iataCode, dto));
    }
}
