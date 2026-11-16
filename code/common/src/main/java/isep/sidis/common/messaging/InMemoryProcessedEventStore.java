package isep.sidis.common.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryProcessedEventStore implements ProcessedEventStore {
    public static final int DEFAULT_CAPACITY = 10_000;

    private final Map<UUID, String> processed;

    public InMemoryProcessedEventStore() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryProcessedEventStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.processed = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public synchronized boolean isProcessed(UUID eventId) {
        return processed.containsKey(eventId);
    }

    @Override
    public synchronized void markProcessed(UUID eventId, String eventType) {
        processed.put(eventId, eventType);
    }

    public synchronized int size() {
        return processed.size();
    }
}
