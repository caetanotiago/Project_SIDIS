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
import isep.psoft.aisafe.flightroutes.services.CalculateNetworkDistanceService;
import isep.psoft.aisafe.flightroutes.services.CountRoutesByAirportService;
import isep.psoft.aisafe.flightroutes.services.CreateFlightRouteService;
import isep.psoft.aisafe.flightroutes.services.GetRouteHistoryService;
import isep.psoft.aisafe.flightroutes.services.ListActiveRoutesService;
import isep.psoft.aisafe.flightroutes.services.SearchAlternativeRoutesService;
import isep.psoft.aisafe.flightroutes.services.SearchFlightRoutesService;
import isep.psoft.aisafe.flightroutes.services.UpdateFlightRouteService;
import isep.psoft.aisafe.flightroutes.services.ViewRoutesByAirportService;
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
 * Rotas de voo (WP#3A & WP#3B). Todos os GET devolvem os dados de TODAS as réplicas: a réplica que
 * recebe o pedido junta os seus dados com os dos peers (se algum peer não responder, a resposta
 * leva o header X-Partial-Response: true). Pedidos vindos de um peer (X-Peer-Hops) são respondidos
 * só com dados locais.
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

    // Consultas usadas pelos outros microsserviços (US203 e US210)
    private final CountRoutesByAirportService countRoutesByAirportService;

    private final FlightRouteAssembler assembler;
    private final ItineraryAssembler itineraryAssembler;

    // US110: Criar Rota (fica guardada nesta réplica)
    @PostMapping
    @Operation(summary = "US110 — Create a flight route between two registered airports")
    public ResponseEntity<FlightRouteDTO> createRoute(@Valid @RequestBody CreateRouteDTO dto) {
        FlightRoute newRoute = createService.createRoute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(newRoute));
    }

    // US113: Consultar detalhes de uma rota pelo ID (local ou numa réplica peer)
    @GetMapping("/{id}")
    @Operation(summary = "US113 — Route details by id (forwarded to the peer replicas when not stored locally)")
    public ResponseEntity<FlightRouteDTO> getRouteById(@PathVariable String id) {
        return ResponseEntity.ok(assembler.toDTO(searchService.getRouteById(id)));
    }

    // US114: Pesquisar rotas (Query params opcionais)
    @GetMapping("/search")
    @Operation(summary = "US114 — Search routes by origin, destination or both (all replicas)")
    public ResponseEntity<List<FlightRouteDTO>> searchRoutes(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String dest) {
        return ResponseEntity.ok(assembler.toDTOList(searchService.searchRoutes(origin, dest)));
    }

    // US203: Rotas ativas compatíveis com uma aeronave (chamado pelo microsserviço Aircraft Management)
    @GetMapping("/compatible")
    @Operation(summary = "US203 — Active routes whose requirements are met by the given range and capacity")
    public ResponseEntity<List<FlightRouteDTO>> getCompatibleRoutes(
            @RequestParam Double range,
            @RequestParam Integer capacity) {
        return ResponseEntity.ok(assembler.toDTOList(searchService.findCompatibleRoutes(range, capacity)));
    }

    // US210: Nº de rotas por aeroporto (chamado pelo microsserviço Airports)
    @GetMapping("/statistics/count-by-airport")
    @Operation(summary = "US210 — Number of routes per airport (origin + destination), keyed by IATA code")
    public ResponseEntity<Map<String, Long>> countRoutesByAirport() {
        return ResponseEntity.ok(countRoutesByAirportService.countRoutesByAirport());
    }

    // US209: Ver todas as rotas que partem de ou chegam a um aeroporto específico (origin OR destination)
    @GetMapping("/by-airport/{iataCode}")
    @Operation(summary = "US209 — View all routes that depart from or arrive at a specific airport")
    public ResponseEntity<List<FlightRouteDTO>> getRoutesByAirport(@PathVariable String iataCode) {
        return ResponseEntity.ok(assembler.toDTOList(viewRoutesByAirportService.findRoutesByAirport(iataCode)));
    }

    // US214: Listar rotas ativas ordenadas por popularidade ou distância
    @GetMapping("/active")
    @Operation(summary = "US214 — List active routes sorted by popularity or distance")
    public ResponseEntity<List<FlightRouteDTO>> listActiveRoutes(
            @RequestParam(defaultValue = "popularity") String sortBy) {
        return ResponseEntity.ok(assembler.toDTOWithUsageList(listActiveRoutesService.listActiveRoutes(sortBy)));
    }

    // US215: Distância total da rede (rotas ativas)
    @GetMapping("/network/total-distance")
    @Operation(summary = "US215 — Calculate the total distance covered by all routes in the network")
    public ResponseEntity<NetworkDistanceDTO> getNetworkTotalDistance() {
        return ResponseEntity.ok(networkDistanceService.calculateTotalDistance());
    }

    // US216: Pesquisar rotas alternativas entre dois aeroportos
    @GetMapping("/alternatives")
    @Operation(summary = "US216 — Search for alternative routes (itineraries) between two airports")
    public ResponseEntity<List<ItineraryDTO>> searchAlternativeRoutes(
            @RequestParam String origin,
            @RequestParam String dest) {
        return ResponseEntity.ok(itineraryAssembler.toDTOList(
                searchAlternativeRoutesService.searchAlternatives(origin, dest)));
    }

    // US112: Atualizar ou Desativar uma rota (executado na réplica dona da rota)
    @PatchMapping("/{id}")
    @Operation(summary = "US112 — Update or deactivate a route (forwarded to the replica that owns it)")
    public ResponseEntity<FlightRouteDTO> updateRoute(
            @PathVariable String id,
            @Valid @RequestBody UpdateRouteDTO dto) {
        return ResponseEntity.ok(updateService.updateRoute(id, dto));
    }

    // US111: Consultar histórico de uma rota
    @GetMapping("/{id}/history")
    @Operation(summary = "US111 — Route change history")
    public ResponseEntity<List<RouteHistoryDTO>> getRouteHistory(@PathVariable String id) {
        return ResponseEntity.ok(historyService.getRouteHistory(id));
    }
}
