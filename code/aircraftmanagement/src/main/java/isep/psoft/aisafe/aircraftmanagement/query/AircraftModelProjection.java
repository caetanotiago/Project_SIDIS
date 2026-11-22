package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies aircraft-model events to this replica's read model {@code REPLICATED_AIRCRAFT_MODEL}, with the same rules
 * as {@link AircraftProjection}: idempotent by eventId, stale versions ignored, unknown types ignored.
 */
@Service
public class AircraftModelProjection {

    private static final Logger log = LoggerFactory.getLogger(AircraftModelProjection.class);

    private final ReplicatedAircraftModelRepository models;
    private final IdempotentEventHandler idempotent;

    public AircraftModelProjection(ReplicatedAircraftModelRepository models, ProcessedEventStore processedEvents) {
        this.models = models;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate and nothing was done */
    @Transactional
    public boolean apply(IntegrationEvent<AircraftModelEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<AircraftModelEventPayload> event) {
        AircraftModelEventPayload p = event.payload();
        if (p == null || p.modelName() == null || p.modelName().isBlank()) {
            throw new IllegalArgumentException("Aircraft-model event " + event.eventId() + " has no modelName");
        }
        switch (event.eventType()) {
            case AircraftModelEvents.AIRCRAFT_MODEL_CREATED, AircraftModelEvents.AIRCRAFT_MODEL_UPDATED -> upsert(event, p);
            default -> log.warn("Ignoring unknown aircraft-model event type {} ({})", event.eventType(), event.eventId());
        }
    }

    private void upsert(IntegrationEvent<AircraftModelEventPayload> event, AircraftModelEventPayload p) {
        ReplicatedAircraftModel row = models.findById(p.modelName())
                .orElseGet(() -> new ReplicatedAircraftModel(p.modelName()));
        if (row.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for model {} (version {} < stored {})", event.eventType(), p.modelName(),
                    p.version(), row.getSourceVersion());
            return;
        }
        row.apply(p, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        models.save(row);
        log.info("Applied {} for model {} (maximumRange {} km, owner {}, version {})", event.eventType(),
                p.modelName(), row.getMaximumRange(), event.sourceInstance(), p.version());
    }
}
