package isep.sidis.common.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable envelope of every event exchanged between AISafe services and replicas over RabbitMQ.
 *
 * <p>JSON form (see docs/Messaging-Conventions.md):
 * <pre>
 * {
 *   "eventId": "5b0f6c1e-2a4b-4f7e-9d6a-1c2b3d4e5f60",
 *   "eventType": "RouteCreated",
 *   "timestamp": "2026-11-10T14:03:21.512Z",
 *   "sourceService": "flightroutes",
 *   "sourceInstance": "flightroutes-1",
 *   "aggregateId": "8c1d...",
 *   "payload": { ... }
 * }
 * </pre>
 *
 * @param eventId        unique id of this event; consumers use it to ignore duplicates (idempotency)
 * @param eventType      past-tense name of what happened, e.g. {@code RouteCreated}
 * @param timestamp      when the event was created, serialised as ISO-8601 in UTC
 * @param sourceService  producing service ({@code spring.application.name}), e.g. {@code flightroutes}
 * @param sourceInstance producing replica ({@code aisafe.instance-id}), e.g. {@code flightroutes-1}
 * @param aggregateId    id of the entity the event is about (route id, registration, IATA code, ...)
 * @param payload        event data; its type is defined by the producer and documented per event
 * @param <T>            payload type
 */
public record IntegrationEvent<T>(
        UUID eventId,
        String eventType,
        Instant timestamp,
        String sourceService,
        String sourceInstance,
        String aggregateId,
        T payload) {

    public IntegrationEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireText(eventType, "eventType");
        Objects.requireNonNull(timestamp, "timestamp");
        requireText(sourceService, "sourceService");
        requireText(sourceInstance, "sourceInstance");
        requireText(aggregateId, "aggregateId");
    }

    /** New event with a random id and the given creation time. Prefer {@link EventPublisher}, which fills the metadata. */
    public static <T> IntegrationEvent<T> create(String eventType, Instant timestamp, String sourceService,
                                                 String sourceInstance, String aggregateId, T payload) {
        return new IntegrationEvent<>(UUID.randomUUID(), eventType, timestamp, sourceService, sourceInstance,
                aggregateId, payload);
    }

    /** {@code true} if this event was published by the given replica (e.g. to skip or tag own events). */
    public boolean isFrom(String instanceId) {
        return sourceInstance.equals(instanceId);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
