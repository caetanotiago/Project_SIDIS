package isep.psoft.aisafe.airports.query.external;

import isep.psoft.aisafe.airports.events.external.FlightRouteEventPayload;
import isep.psoft.aisafe.airports.events.external.FlightRoutesEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

// Projeção das rotas
@Service
public class RouteCountProjection {
    private static final Logger log = LoggerFactory.getLogger(RouteCountProjection.class);

    private final KnownRouteRepository repository;
    private final IdempotentEventHandler idempotent;

    // Construtor
    public RouteCountProjection(KnownRouteRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    // Aplica evento (idempotente)
    @Transactional
    public boolean apply(IntegrationEvent<FlightRouteEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    // Rotas por aeroporto
    @Transactional(readOnly = true)
    public Map<String, Long> routeCountsByAirport() {
        Map<String, Long> counts = new TreeMap<>();
        for (KnownRoute route : repository.findAll()) {
            if (route.getOriginIata() != null) counts.merge(route.getOriginIata(), 1L, Long::sum);
            if (route.getDestinationIata() != null) counts.merge(route.getDestinationIata(), 1L, Long::sum);
        }
        return counts;
    }

    // Escolhe pelo tipo
    private void applyOnce(IntegrationEvent<FlightRouteEventPayload> event) {
        FlightRouteEventPayload payload = event.payload();
        if (payload == null || payload.routeId() == null) {
            throw new IllegalArgumentException("Route event " + event.eventId() + " has no routeId");
        }
        switch (event.eventType()) {
            case FlightRoutesEvents.ROUTE_CREATED, FlightRoutesEvents.ROUTE_UPDATED -> upsert(event, payload);
            default -> log.warn("Ignoring unknown route event type {} ({})", event.eventType(), event.eventId());
        }
    }

    // Insere ou atualiza rota
    private void upsert(IntegrationEvent<FlightRouteEventPayload> event, FlightRouteEventPayload payload) {
        KnownRoute route = repository.findById(payload.routeId()).orElseGet(() -> new KnownRoute(payload.routeId()));
        if (route.isNewerThan(payload.version())) {
            log.info("Ignoring stale {} for route {} (version {} < stored {})", event.eventType(), payload.routeId(),
                    payload.version(), route.getSourceVersion());
            return;
        }
        route.apply(payload);
        repository.save(route);
        log.info("Applied {} for route {} {}->{} (from {})", event.eventType(), payload.routeId(),
                payload.originIata(), payload.destinationIata(), event.sourceInstance());
    }
}
