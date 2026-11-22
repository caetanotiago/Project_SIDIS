package isep.psoft.aisafe.maintenance.query.external;

import isep.psoft.aisafe.maintenance.events.external.AircraftManagementEvents;
import isep.psoft.aisafe.maintenance.events.external.AircraftModelPayload;
import isep.psoft.aisafe.maintenance.events.external.AircraftPayload;
import isep.psoft.aisafe.maintenance.events.external.FlightRoutesEvents;
import isep.psoft.aisafe.maintenance.events.external.ScheduledFlightPayload;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ExternalEventsProjection {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventsProjection.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final KnownAircraftRepository aircraft;
    private final KnownAircraftModelRepository models;
    private final KnownScheduledFlightRepository flights;
    private final IdempotentEventHandler idempotent;

    public ExternalEventsProjection(KnownAircraftRepository aircraft, KnownAircraftModelRepository models,
                                    KnownScheduledFlightRepository flights, ProcessedEventStore processedEvents) {
        this.aircraft = aircraft;
        this.models = models;
        this.flights = flights;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    @Transactional
    public boolean apply(IntegrationEvent<?> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<?> event) {
        switch (event.eventType()) {
            case AircraftManagementEvents.AIRCRAFT_REGISTERED, AircraftManagementEvents.AIRCRAFT_STATUS_CHANGED -> {
                AircraftPayload p = payload(event, AircraftPayload.class);
                require(p.registrationNumber(), "registrationNumber", event);
                KnownAircraft row = aircraft.findById(p.registrationNumber())
                        .orElseGet(() -> new KnownAircraft(p.registrationNumber()));
                if (row.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for aircraft {}", event.eventType(), p.registrationNumber());
                    return;
                }
                row.apply(p, event.timestamp());
                aircraft.save(row);
                log.info("Applied {} for aircraft {} (status {}) from {}", event.eventType(), p.registrationNumber(),
                        row.getStatus(), event.sourceInstance());
            }
            case AircraftManagementEvents.AIRCRAFT_MODEL_CREATED, AircraftManagementEvents.AIRCRAFT_MODEL_UPDATED -> {
                AircraftModelPayload p = payload(event, AircraftModelPayload.class);
                require(p.modelName(), "modelName", event);
                KnownAircraftModel row = models.findById(p.modelName())
                        .orElseGet(() -> new KnownAircraftModel(p.modelName()));
                if (row.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for model {}", event.eventType(), p.modelName());
                    return;
                }
                row.apply(p.manufacturer(), p.version(), KnownAircraft.FROM_EVENT, event.timestamp());
                models.save(row);
                log.info("Applied {} for model {} from {}", event.eventType(), p.modelName(), event.sourceInstance());
            }
            case FlightRoutesEvents.SCHEDULED_FLIGHT_CREATED, FlightRoutesEvents.SCHEDULED_FLIGHT_STATUS_CHANGED -> {
                ScheduledFlightPayload p = payload(event, ScheduledFlightPayload.class);
                require(p.flightId(), "flightId", event);
                KnownScheduledFlight row = flights.findById(p.flightId())
                        .orElseGet(() -> new KnownScheduledFlight(p.flightId()));
                if (row.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for flight {}", event.eventType(), p.flightId());
                    return;
                }
                row.apply(p, event.timestamp());
                flights.save(row);
                log.info("Applied {} for flight {} ({} min, aircraft {}) from {}", event.eventType(), p.flightId(),
                        row.getEstimatedFlightTime(), row.getAircraftRegistration(), event.sourceInstance());
            }
            default -> log.debug("Ignoring {} from {} (not used by Maintenance)", event.eventType(),
                    event.sourceService());
        }
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
