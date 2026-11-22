package isep.psoft.aisafe.flightroutes.query.external;

import isep.psoft.aisafe.flightroutes.events.external.AircraftEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.AircraftModelEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.AirportEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.ExternalEvents;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies Aircraft Management and Airports events to the local read models {@code KNOWN_AIRCRAFT},
 * {@code KNOWN_AIRCRAFT_MODEL} and {@code KNOWN_AIRPORT}. Same rules as the projections of Flight Routes' own events:
 * idempotent by eventId, stale versions ignored, unknown types ignored (so the producers can add events freely).
 * Fed by {@link ExternalEventsListener} only when {@code aisafe.flightroutes.external-events.enabled=true}.
 */
@Service
public class ExternalEventsProjection {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventsProjection.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final KnownAircraftRepository aircraft;
    private final KnownAircraftModelRepository models;
    private final KnownAirportRepository airports;
    private final IdempotentEventHandler idempotent;

    public ExternalEventsProjection(KnownAircraftRepository aircraft, KnownAircraftModelRepository models,
                                    KnownAirportRepository airports, ProcessedEventStore processedEvents) {
        this.aircraft = aircraft;
        this.models = models;
        this.airports = airports;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate */
    @Transactional
    public boolean apply(IntegrationEvent<?> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<?> event) {
        switch (event.eventType()) {
            case ExternalEvents.AIRCRAFT_REGISTERED, ExternalEvents.AIRCRAFT_STATUS_CHANGED -> {
                AircraftEventPayload p = payload(event, AircraftEventPayload.class);
                require(p.registrationNumber(), "registrationNumber", event);
                KnownAircraft a = aircraft.findById(p.registrationNumber())
                        .orElseGet(() -> new KnownAircraft(p.registrationNumber()));
                if (a.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for aircraft {}", event.eventType(), p.registrationNumber());
                    return;
                }
                a.apply(p);
                aircraft.save(a);
            }
            case ExternalEvents.AIRCRAFT_MODEL_CREATED, ExternalEvents.AIRCRAFT_MODEL_UPDATED -> {
                AircraftModelEventPayload p = payload(event, AircraftModelEventPayload.class);
                require(p.modelName(), "modelName", event);
                KnownAircraftModel m = models.findById(p.modelName()).orElseGet(() -> new KnownAircraftModel(p.modelName()));
                if (m.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for aircraft model {}", event.eventType(), p.modelName());
                    return;
                }
                m.apply(p);
                models.save(m);
            }
            case ExternalEvents.AIRPORT_CREATED, ExternalEvents.AIRPORT_UPDATED, ExternalEvents.AIRPORT_STATUS_CHANGED -> {
                AirportEventPayload p = payload(event, AirportEventPayload.class);
                require(p.iataCode(), "iataCode", event);
                String code = p.iataCode().toUpperCase();
                KnownAirport ap = airports.findById(code).orElseGet(() -> new KnownAirport(code));
                if (ap.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for airport {}", event.eventType(), code);
                    return;
                }
                ap.apply(p);
                airports.save(ap);
            }
            default -> {
                log.debug("Ignoring {} from {} (not used by Flight Routes)", event.eventType(), event.sourceService());
                return;
            }
        }
        log.info("Applied {} {} from {}", event.eventType(), event.aggregateId(), event.sourceInstance());
    }

    private static <T> T payload(IntegrationEvent<?> event, Class<T> type) {
        Object raw = event.payload();
        if (raw == null) {
            throw new IllegalArgumentException(event.eventType() + " " + event.eventId() + " has no payload");
        }
        return type.isInstance(raw) ? type.cast(raw) : JSON.convertValue(raw, type);
    }

    private static void require(String value, String field, IntegrationEvent<?> event) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(event.eventType() + " " + event.eventId() + " has no " + field);
        }
    }
}
