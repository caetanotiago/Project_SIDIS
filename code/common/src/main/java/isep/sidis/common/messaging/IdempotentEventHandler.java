package isep.sidis.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class IdempotentEventHandler {
    private static final Logger log = LoggerFactory.getLogger(IdempotentEventHandler.class);

    private final ProcessedEventStore store;

    public IdempotentEventHandler(ProcessedEventStore store) {
        this.store = store;
    }

    public <T> boolean handle(IntegrationEvent<T> event, Consumer<IntegrationEvent<T>> handler) {
        if (store.isProcessed(event.eventId())) {
            log.info("Ignoring duplicate {} {} from {}", event.eventType(), event.eventId(), event.sourceInstance());
            return false;
        }
        handler.accept(event);
        store.markProcessed(event.eventId(), event.eventType());
        return true;
    }
}
