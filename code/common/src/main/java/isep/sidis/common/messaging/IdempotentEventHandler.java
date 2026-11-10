package isep.sidis.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Runs an event handler at most once per {@code eventId} on this instance.
 *
 * <pre>
 * &#64;RabbitListener(queues = "#{flightRoutesEvents.queueName}")
 * &#64;Transactional
 * public void on(IntegrationEvent&lt;RoutePayload&gt; event) {
 *     idempotent.handle(event, e -&gt; apply(e.payload()));
 * }
 * </pre>
 *
 * <ul>
 *   <li>already processed → the handler is not called and {@code false} is returned (the message is acked);</li>
 *   <li>handler succeeds → the event id is recorded and {@code true} is returned;</li>
 *   <li>handler throws → nothing is recorded and the exception propagates, so the listener container retries
 *       and finally sends the message to the dead-letter queue.</li>
 * </ul>
 * The check and the record are not atomic with the handler's side effects unless the store and the handler share
 * one transaction (see {@link ProcessedEventStore}); handlers should therefore also be idempotent (upserts).
 */
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
