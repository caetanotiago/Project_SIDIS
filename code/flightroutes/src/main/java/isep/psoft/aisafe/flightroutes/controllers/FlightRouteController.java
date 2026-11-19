package isep.psoft.aisafe.flightroutes.controllers;

import isep.psoft.aisafe.flightroutes.assemblers.FlightRouteAssembler;
import isep.psoft.aisafe.flightroutes.assemblers.ItineraryAssembler;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.CreateRouteDTO;
import isep.psoft.aisafe.flightroutes.dto.FlightRouteDTO;
import isep.psoft.aisafe.flightroutes.dto.ItineraryDTO;
import isep.psoft.aisafe.flightroutes.dto.NetworkDistanceDTO;
import isep.psoft.aisafe.flightroutes.dto.RouteHistoryDTO;
import isep.psoft.aisafe.flightroutes.dto.UpdateRouteDTO;
import isep.psoft.aisafe.flightroutes.query.CalculateNetworkDistanceService;
import isep.psoft.aisafe.flightroutes.query.CountRoutesByAirportService;
import isep.psoft.aisafe.flightroutes.command.CreateFlightRouteService;
import isep.psoft.aisafe.flightroutes.query.GetRouteHistoryService;
import isep.psoft.aisafe.flightroutes.query.ListActiveRoutesService;
import isep.psoft.aisafe.flightroutes.query.SearchAlternativeRoutesService;
import isep.psoft.aisafe.flightroutes.query.SearchFlightRoutesService;
import isep.psoft.aisafe.flightroutes.command.UpdateFlightRouteService;
import isep.psoft.aisafe.flightroutes.query.ViewRoutesByAirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Flight routes (WP#3A & WP#3B), Assignment 2 CQRS:
 * <ul>
 *   <li>QUERIES (every GET) are answered from THIS replica's read model, which holds the routes of every replica
 *       (built from route events). No peer is called and no X-Partial-Response header is needed: a replica keeps
 *       answering even when all the others are down, possibly with slightly stale data (eventual consistency).</li>
 *   <li>COMMANDS (POST, PATCH) run on the write model of the replica that owns the route; a PATCH received by
 *       another replica is forwarded to the owner.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Flight Routes", description = "Route management and operations (WP#3A & WP#3B)")
public class FlightRouteController {

    private final CreateFlightRouteService createService;
    private final UpdateFlightRouteService updateService;
    private final GetRouteHistoryService historyService;
    private final SearchFlightRoutesService searchService;

    // WP#3B (US214/US215/US216)
    private final ListActiveRoutesService listActiveRoutesService;
    private final CalculateNetworkDistanceService networkDistanceService;
    private final SearchAlternativeRoutesService searchAlternativeRoutesService;

    // WP#2B (US209)
    private final ViewRoutesByAirportService viewRoutesByAirportService;

    // Queries used by the other microservices (US203 and US210)
    private final CountRoutesByAirportService countRoutesByAirportService;

    private final FlightRouteAssembler assembler;
    private final ItineraryAssembler itineraryAssembler;

    // US110 (command): create a route (stored in, and owned by, this replica)
    @PostMapping
    @Operation(summary = "US110 — Create a flight route between two registered airports")
    public ResponseEntity<FlightRouteDTO> createRoute(@Valid @RequestBody CreateRouteDTO dto) {
        FlightRoute newRoute = createService.createRoute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(newRoute));
    }

    // US113 (query): route details by id, from the local read model
    @GetMapping("/{id}")
    @Operation(summary = "US113 — Route details by id (from this replica's read model)")
    public ResponseEntity<FlightRouteDTO> getRouteById(@PathVariable String id) {
        return ResponseEntity.ok(assembler.toDTO(searchService.getRouteById(id)));
    }

    // US114 (query): search routes (optional query params)
    @GetMapping("/search")
    @Operation(summary = "US114 — Search routes by origin, destination or both (read model: routes of all replicas)")
    public ResponseEntity<List<FlightRouteDTO>> searchRoutes(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String dest) {
        return ResponseEntity.ok(assembler.toDTOList(searchService.searchRoutes(origin, dest)));
    }

    // US203 (query, B6 provider): active routes compatible with an aircraft (called by Aircraft Management)
    @GetMapping("/compatible")
    @Operation(summary = "US203 — Active routes whose requirements are met by the given range and capacity")
    public ResponseEntity<List<FlightRouteDTO>> getCompatibleRoutes(
            @RequestParam Double range,
            @RequestParam Integer capacity) {
        return ResponseEntity.ok(assembler.toDTOList(searchService.findCompatibleRoutes(range, capacity)));
    }

    // US210 (query, B5 provider): number of routes per airport (called by Airports)
    @GetMapping("/statistics/count-by-airport")
    @Operation(summary = "US210 — Number of routes per airport (origin + destination), keyed by IATA code")
    public ResponseEntity<Map<String, Long>> countRoutesByAirport() {
        return ResponseEntity.ok(countRoutesByAirportService.countRoutesByAirport());
    }

    // US209 (query): routes departing from or arriving at an airport (origin OR destination)
    @GetMapping("/by-airport/{iataCode}")
    @Operation(summary = "US209 — View all routes that depart from or arrive at a specific airport")
    public ResponseEntity<List<FlightRouteDTO>> getRoutesByAirport(@PathVariable String iataCode) {
        return ResponseEntity.ok(assembler.toDTOList(viewRoutesByAirportService.findRoutesByAirport(iataCode)));
    }

    // US214 (query): active routes sorted by popularity or distance
    @GetMapping("/active")
    @Operation(summary = "US214 — List active routes sorted by popularity or distance")
    public ResponseEntity<List<FlightRouteDTO>> listActiveRoutes(
            @RequestParam(defaultValue = "popularity") String sortBy) {
        return ResponseEntity.ok(assembler.toDTOWithUsageList(listActiveRoutesService.listActiveRoutes(sortBy)));
    }

    // US215 (query): total distance of the network (active routes)
    @GetMapping("/network/total-distance")
    @Operation(summary = "US215 — Calculate the total distance covered by all routes in the network")
    public ResponseEntity<NetworkDistanceDTO> getNetworkTotalDistance() {
        return ResponseEntity.ok(networkDistanceService.calculateTotalDistance());
    }

    // US216 (query): alternative routes between two airports
    @GetMapping("/alternatives")
    @Operation(summary = "US216 — Search for alternative routes (itineraries) between two airports")
    public ResponseEntity<List<ItineraryDTO>> searchAlternativeRoutes(
            @RequestParam String origin,
            @RequestParam String dest) {
        return ResponseEntity.ok(itineraryAssembler.toDTOList(
                searchAlternativeRoutesService.searchAlternatives(origin, dest)));
    }

    // US112 (command): update or deactivate a route (executed by the replica that owns it)
    @PatchMapping("/{id}")
    @Operation(summary = "US112 — Update or deactivate a route (forwarded to the replica that owns it)")
    public ResponseEntity<FlightRouteDTO> updateRoute(
            @PathVariable String id,
            @Valid @RequestBody UpdateRouteDTO dto) {
        return ResponseEntity.ok(updateService.updateRoute(id, dto));
    }

    // US111 (query): route change history, from the local read model
    @GetMapping("/{id}/history")
    @Operation(summary = "US111 — Route change history")
    public ResponseEntity<List<RouteHistoryDTO>> getRouteHistory(@PathVariable String id) {
        return ResponseEntity.ok(historyService.getRouteHistory(id));
    }
}
