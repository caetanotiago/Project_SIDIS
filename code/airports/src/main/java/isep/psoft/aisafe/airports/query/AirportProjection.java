package isep.psoft.aisafe.airports.query;

import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Projeção REPLICATED_AIRPORT
@Service
public class AirportProjection {
    private static final Logger log = LoggerFactory.getLogger(AirportProjection.class);

    private final ReplicatedAirportRepository repository;
    private final IdempotentEventHandler idempotent;

    // Construtor
    public AirportProjection(ReplicatedAirportRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    // Aplica evento (idempotente)
    @Transactional
    public boolean apply(IntegrationEvent<AirportEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    // Escolhe pelo tipo
    private void applyOnce(IntegrationEvent<AirportEventPayload> event) {
        AirportEventPayload payload = event.payload();
        if (payload == null || payload.iataCode() == null) {
            throw new IllegalArgumentException("Airport event " + event.eventId() + " has no iataCode");
        }
        switch (event.eventType()) {
            case AirportEvents.AIRPORT_CREATED, AirportEvents.AIRPORT_UPDATED, AirportEvents.AIRPORT_STATUS_CHANGED ->
                    upsert(event, payload);
            default -> log.warn("Ignoring unknown airport event type {} ({})", event.eventType(), event.eventId());
        }
    }

    // Insere ou atualiza linha
    private void upsert(IntegrationEvent<AirportEventPayload> event, AirportEventPayload payload) {
        ReplicatedAirport airport = repository.findById(payload.iataCode())
                .orElseGet(() -> new ReplicatedAirport(payload.iataCode()));
        if (airport.isNewerThan(payload.version())) {
            log.info("Ignoring stale {} for airport {} (version {} < stored {})", event.eventType(),
                    payload.iataCode(), payload.version(), airport.getSourceVersion());
            return;
        }
        airport.apply(payload, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        repository.save(airport);
        log.info("Applied {} for airport {} (owner {}, version {}, status {})", event.eventType(),
                payload.iataCode(), event.sourceInstance(), payload.version(), payload.status());
    }
}
