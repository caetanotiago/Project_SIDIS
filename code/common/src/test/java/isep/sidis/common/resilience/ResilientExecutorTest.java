package isep.sidis.common.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientExecutorTest {

    private final CircuitBreakerRegistry registry =
            new CircuitBreakerRegistry(2, Duration.ofMinutes(1), Clock.systemUTC());
    private final ResilientExecutor executor = new ResilientExecutor(registry, 2, Duration.ofMillis(1));

    @Test
    void retriesTransientFailuresUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute("peer", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("connection refused");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get()); // 1 tentativa + 2 retries
        assertEquals(CircuitBreaker.State.CLOSED, registry.get("peer").getState());
    }

    @Test
    void givesUpAfterMaxRetriesAndRecordsFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ResourceAccessException.class, () -> executor.execute("peer", () -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("timeout");
        }));

        assertEquals(3, calls.get());
        assertEquals(1, registry.get("peer").getConsecutiveFailures());
    }

    @Test
    void doesNotRetryClientErrors() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(HttpClientErrorException.class, () -> executor.execute("peer", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null);
        }));

        assertEquals(1, calls.get());
        assertEquals(0, registry.get("peer").getConsecutiveFailures()); // o destino respondeu
    }

    @Test
    void openCircuitFailsFastWithoutCallingTheTarget() {
        for (int i = 0; i < 2; i++) {
            assertThrows(ResourceAccessException.class, () -> executor.execute("down", () -> {
                throw new ResourceAccessException("down");
            }));
        }
        AtomicInteger calls = new AtomicInteger();

        assertThrows(CircuitOpenException.class, () -> executor.execute("down", () -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
    }
}
