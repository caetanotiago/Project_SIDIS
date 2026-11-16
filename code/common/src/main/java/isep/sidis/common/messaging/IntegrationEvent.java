package isep.sidis.common.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    public static <T> IntegrationEvent<T> create(String eventType, Instant timestamp, String sourceService,
                                                 String sourceInstance, String aggregateId, T payload) {
        return new IntegrationEvent<>(UUID.randomUUID(), eventType, timestamp, sourceService, sourceInstance,
                aggregateId, payload);
    }

    public boolean isFrom(String instanceId) {
        return sourceInstance.equals(instanceId);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
