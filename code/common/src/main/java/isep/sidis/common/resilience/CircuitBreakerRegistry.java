package isep.sidis.common.resilience;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerRegistry {
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreakerRegistry(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public CircuitBreaker get(String target) {
        return breakers.computeIfAbsent(target,
                t -> new CircuitBreaker(t, failureThreshold, openDuration, clock));
    }

    public Map<String, CircuitBreaker> all() {
        return Map.copyOf(breakers);
    }
}
