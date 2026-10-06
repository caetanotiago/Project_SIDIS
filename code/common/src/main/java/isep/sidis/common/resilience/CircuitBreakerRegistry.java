package isep.sidis.common.resilience;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Um circuit breaker por destino remoto (base URL), criado na primeira utilização. */
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

    /** Estado atual de todos os destinos já contactados (para /actuator/health). */
    public Map<String, CircuitBreaker> all() {
        return Map.copyOf(breakers);
    }
}
