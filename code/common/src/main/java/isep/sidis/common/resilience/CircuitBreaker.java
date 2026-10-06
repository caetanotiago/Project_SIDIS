package isep.sidis.common.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Circuit breaker de um destino remoto (uma réplica/serviço, identificado pelo base URL).
 *
 * <ul>
 *   <li>CLOSED — os pedidos passam; ao fim de {@code failureThreshold} falhas consecutivas abre.</li>
 *   <li>OPEN — os pedidos falham logo (sem rede) durante {@code openDuration}.</li>
 *   <li>HALF_OPEN — passado esse tempo deixa passar pedidos de teste: um sucesso fecha o circuito,
 *       uma falha volta a abri-lo.</li>
 * </ul>
 * Evita falhas em cascata e dá tempo à réplica para recuperar (slides: Circuit Breaker Pattern).
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String target;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt;

    public CircuitBreaker(String target, int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.target = target;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    /** {@code true} se o pedido pode ser enviado agora. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
        }
        return state != State.OPEN;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    public synchronized State getState() {
        // Reflete a passagem a HALF_OPEN mesmo sem pedidos (útil no health check)
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            return State.HALF_OPEN;
        }
        return state;
    }

    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public String getTarget() {
        return target;
    }
}
