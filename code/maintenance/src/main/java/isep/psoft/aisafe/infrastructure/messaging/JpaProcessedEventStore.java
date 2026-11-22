package isep.psoft.aisafe.infrastructure.messaging;

import isep.sidis.common.messaging.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventRepository repository;

    @Override
    public boolean isProcessed(UUID eventId) {
        return repository.existsById(eventId.toString());
    }

    @Override
    public void markProcessed(UUID eventId, String eventType) {
        repository.save(new ProcessedEvent(eventId.toString(), eventType, Clock.systemUTC().instant()));
    }
}
