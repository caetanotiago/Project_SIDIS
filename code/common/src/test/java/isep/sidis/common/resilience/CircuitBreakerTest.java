package isep.sidis.common.resilience;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    /** Relógio controlado pelo teste. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void opensAfterThresholdConsecutiveFailures() {
        CircuitBreaker cb = new CircuitBreaker("peer", 3, Duration.ofSeconds(10), new MutableClock());

        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());

        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker("peer", 2, Duration.ofSeconds(10), new MutableClock());

        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void halfOpensAfterOpenDurationAndClosesOnSuccess() {
        MutableClock clock = new MutableClock();
        CircuitBreaker cb = new CircuitBreaker("peer", 1, Duration.ofSeconds(10), clock);
        cb.recordFailure();
        assertFalse(cb.allowRequest());

        clock.advance(Duration.ofSeconds(10));
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void failureInHalfOpenReopensImmediately() {
        MutableClock clock = new MutableClock();
        CircuitBreaker cb = new CircuitBreaker("peer", 3, Duration.ofSeconds(5), clock);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(5));
        assertTrue(cb.allowRequest()); // HALF_OPEN

        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }
}
