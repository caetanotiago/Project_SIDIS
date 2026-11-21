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

/**
 * Aplica os eventos de aeroportos ao read model {@code REPLICATED_AIRPORT} desta réplica.
 *
 * <ul>
 *   <li><b>Idempotente:</b> um {@code eventId} já processado por esta réplica é ignorado, e o registo do id
 *       fica gravado na mesma transação que a alteração ({@code JpaProcessedEventStore}).</li>
 *   <li><b>Eventos próprios:</b> a réplica dona aplica o seu evento dentro da transação de escrita
 *       (read-your-writes); quando o broker lho volta a entregar, o id já está registado e é ignorado.</li>
 *   <li><b>Ordem:</b> cada aeroporto só é escrito por uma réplica (a dona) e cada fila tem um consumidor, por isso
 *       os eventos de um aeroporto chegam por ordem. Por segurança, um evento com {@code version} inferior à
 *       guardada é ignorado (ex.: mensagem reenviada a partir da DLQ).</li>
 *   <li>Um {@code eventType} desconhecido é ignorado (fica no log), para o produtor poder acrescentar eventos.</li>
 * </ul>
 */
@Service
public class AirportProjection {

    private static final Logger log = LoggerFactory.getLogger(AirportProjection.class);

    private final ReplicatedAirportRepository repository;
    private final IdempotentEventHandler idempotent;

    public AirportProjection(ReplicatedAirportRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} se o evento era duplicado e nada foi feito */
    @Transactional
    public boolean apply(IntegrationEvent<AirportEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<AirportEventPayload> event) {
        AirportEventPayload payload = event.payload();
        if (payload == null || payload.iataCode() == null) {
            // Exceção = falha: a mensagem é repetida e depois vai para a DLQ.
            throw new IllegalArgumentException("Airport event " + event.eventId() + " has no iataCode");
        }
        switch (event.eventType()) {
            case AirportEvents.AIRPORT_CREATED, AirportEvents.AIRPORT_UPDATED, AirportEvents.AIRPORT_STATUS_CHANGED ->
                    upsert(event, payload);
            default -> log.warn("Ignoring unknown airport event type {} ({})", event.eventType(), event.eventId());
        }
    }

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
