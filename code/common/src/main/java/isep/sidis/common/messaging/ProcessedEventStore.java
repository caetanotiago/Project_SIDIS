package isep.sidis.common.messaging;

import java.util.UUID;

public interface ProcessedEventStore {
    boolean isProcessed(UUID eventId);

    void markProcessed(UUID eventId, String eventType);
}
