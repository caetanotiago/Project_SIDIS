package isep.psoft.aisafe.airports.controllers;

import isep.psoft.aisafe.airports.assemblers.AirportAssembler;
import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.services.RegisterAirportService;
import isep.psoft.aisafe.airports.services.SearchAirportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Aeroportos (WP#2A & WP#2B). Todos os GET devolvem os dados de TODAS as réplicas: a réplica que
 * recebe o pedido junta os seus dados com os dos peers (se algum peer não responder, a resposta
 * leva o header X-Partial-Response: true). Pedidos vindos de um peer (X-Peer-Hops) são respondidos
 * só com dados locais. Papéis por endpoint: AirportsAuthorizationRules.
 */
@RestController
@RequestMapping("/api/airports")
@RequiredArgsConstructor
@Tag(name = "Airports", description = "Airport management (WP#2A & WP#2B)")
public class AirportController {

    private final RegisterAirportService registerService;
    private final SearchAirportsService searchService;
    private final AirportAssembler assembler;

    // US106 / US207: registar aeroporto (fica guardado nesta réplica)
    @PostMapping
    @Operation(summary = "US106/US207 — Register an airport with runways and optional facilities")
    public ResponseEntity<AirportDTO> register(@Valid @RequestBody CreateAirportDTO dto) {
        Airport airport = registerService.register(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{iata}").buildAndExpand(airport.getIataCode()).toUri();
        return ResponseEntity.created(location).body(assembler.toDTO(airport));
    }

    // US107: detalhes pelo IATA (local ou numa réplica peer). Contrato B2 do Flight Routes.
    @GetMapping("/{iataCode}")
    @Operation(summary = "US107 — Airport details by IATA code (forwarded to the peer replicas when not stored locally)")
    public ResponseEntity<AirportDTO> getByIata(@PathVariable String iataCode) {
        return ResponseEntity.ok(searchService.getByIata(iataCode));
    }

    // US108: pesquisar por cidade, país ou nome (filtros opcionais e combináveis)
    @GetMapping
    @Operation(summary = "US108 — Search airports by city, country or name (all replicas)")
    public ResponseEntity<List<AirportDTO>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(searchService.search(city, country, name));
    }
}
