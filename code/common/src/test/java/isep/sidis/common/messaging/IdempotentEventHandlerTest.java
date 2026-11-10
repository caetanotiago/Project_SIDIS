package isep.sidis.common.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentEventHandlerTest {

    private final InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
    private final IdempotentEventHandler idempotent = new IdempotentEventHandler(store);

    private static IntegrationEvent<String> event() {
        return IntegrationEvent.create("RouteCreated", Instant.now(), "flightroutes", "flightroutes-1", "r-1", "p");
    }

    @Test
    void aRedeliveredEventIsHandledOnlyOnce() {
        IntegrationEvent<String> event = event();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(idempotent.handle(event, e -> calls.incrementAndGet()));
        assertFalse(idempotent.handle(event, e -> calls.incrementAndGet()));

        assertEquals(1, calls.get());
        assertTrue(store.isProcessed(event.eventId()));
    }

    @Test
    void differentEventsAreAllHandled() {
        AtomicInteger calls = new AtomicInteger();

        idempotent.handle(event(), e -> calls.incrementAndGet());
        idempotent.handle(event(), e -> calls.incrementAndGet());

        assertEquals(2, calls.get());
    }

    @Test
    void aFailedEventIsNotRecordedSoTheRetryRunsTheHandlerAgain() {
        IntegrationEvent<String> event = event();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> idempotent.handle(event, e -> {
            calls.incrementAndGet();
            throw new IllegalStateException("database down");
        }));
        assertFalse(store.isProcessed(event.eventId()));

        assertTrue(idempotent.handle(event, e -> calls.incrementAndGet()));
        assertEquals(2, calls.get());
    }

    @Test
    void theInMemoryStoreForgetsTheOldestIdsBeyondItsCapacity() {
        InMemoryProcessedEventStore small = new InMemoryProcessedEventStore(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        small.markProcessed(first, "A");
        small.markProcessed(second, "B");
        small.markProcessed(third, "C");

        assertEquals(2, small.size());
        assertFalse(small.isProcessed(first));
        assertTrue(small.isProcessed(second));
        assertTrue(small.isProcessed(third));
    }
}
