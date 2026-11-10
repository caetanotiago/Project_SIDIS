package isep.sidis.common.messaging;

import java.util.UUID;

/**
 * Remembers which events this instance has already processed (consumer-side idempotency).
 *
 * <p>The broker delivers <b>at least once</b>: after a consumer crash, a lost ack or a retry the same event can
 * arrive again. {@link IdempotentEventHandler} checks this store before running a handler and records the event
 * after the handler succeeds.
 *
 * <p>The default bean is {@link InMemoryProcessedEventStore}. A service with a database should provide a
 * JPA-backed implementation and call the handler inside the same {@code @Transactional} method that applies
 * the event, so "apply the change" and "remember the event id" commit or roll back together.
 */
public interface ProcessedEventStore {

    boolean isProcessed(UUID eventId);

    void markProcessed(UUID eventId, String eventType);
}
