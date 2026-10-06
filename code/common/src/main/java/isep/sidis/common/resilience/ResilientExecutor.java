package isep.sidis.common.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Executa uma chamada remota com circuit breaker + retry com exponential backoff.
 *
 * <p>Só as falhas <b>transitórias</b> contam (rede: timeout/ligação recusada, ou 5xx do destino):
 * são repetidas e, esgotadas as tentativas, registadas no circuit breaker. Uma resposta 4xx
 * (ex.: 404) significa que o destino está vivo — conta como sucesso e é propagada sem retry.
 */
public class ResilientExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResilientExecutor.class);

    private final CircuitBreakerRegistry breakers;
    private final int maxRetries;
    private final Duration initialBackoff;

    public ResilientExecutor(CircuitBreakerRegistry breakers, int maxRetries, Duration initialBackoff) {
        this.breakers = breakers;
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoff = initialBackoff;
    }

    public <T> T execute(String target, Supplier<T> call) {
        CircuitBreaker breaker = breakers.get(target);
        if (!breaker.allowRequest()) {
            throw new CircuitOpenException(target);
        }

        long backoffMs = initialBackoff.toMillis();
        for (int attempt = 0; ; attempt++) {
            try {
                T result = call.get();
                breaker.recordSuccess();
                return result;
            } catch (RuntimeException e) {
                if (!isTransient(e)) {
                    breaker.recordSuccess(); // o destino respondeu (ex.: 404/409)
                    throw e;
                }
                if (attempt >= maxRetries) {
                    breaker.recordFailure();
                    log.warn("Call to {} failed after {} attempt(s): {} (circuit {})",
                            target, attempt + 1, e.getMessage(), breaker.getState());
                    throw e;
                }
                log.debug("Transient failure calling {} (attempt {}), retrying in {} ms: {}",
                        target, attempt + 1, backoffMs, e.getMessage());
                sleep(backoffMs);
                backoffMs *= 2;
            }
        }
    }

    /** Falhas que justificam retry/failover para outra réplica. */
    public static boolean isTransient(Throwable e) {
        return e instanceof ResourceAccessException
                || e instanceof HttpServerErrorException
                || e instanceof CircuitOpenException;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("Interrupted while waiting to retry");
        }
    }
}
