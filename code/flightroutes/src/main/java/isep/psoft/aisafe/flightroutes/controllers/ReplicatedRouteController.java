package isep.psoft.aisafe.flightroutes.controllers;

import isep.psoft.aisafe.flightroutes.dto.ReplicatedRouteDTO;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRouteQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Event-built read model of the replica that answers (Assignment 2 proof of concept). Unlike the other GETs it
 * never asks the peers: each replica answers from its own database, so calling it on every replica shows whether
 * they have converged. Empty when messaging is off. Role: ATCC (rule {@code GET /api/routes/**}).
 */
@RestController
@RequestMapping("/api/routes/replicated")
@RequiredArgsConstructor
@Tag(name = "Flight Routes - replicated read model", description = "Routes received through RabbitMQ events (local to this replica)")
public class ReplicatedRouteController {

    private final ReplicatedRouteQueryService queries;

    @GetMapping
    @Operation(summary = "All routes in this replica's event-built read model (no peer calls)")
    public ResponseEntity<List<ReplicatedRouteDTO>> findAll() {
        return ResponseEntity.ok(queries.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One route from this replica's event-built read model (404 until its event arrives)")
    public ResponseEntity<ReplicatedRouteDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(queries.findById(id));
    }
}
