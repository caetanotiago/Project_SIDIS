package isep.sidis.common.messaging;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

import java.time.Duration;

/**
 * Bounded retry of a failing listener: {@code maxAttempts} deliveries in total (with exponential back-off),
 * then the message is rejected without requeue, so the broker moves it to the queue's dead-letter queue.
 */
public final class ConsumerRetry {

    private ConsumerRetry() {
    }

    /**
     * @param maxAttempts     total attempts, including the first one (1 = no retry)
     * @param initialInterval wait before the first retry
     * @param multiplier      factor applied to the wait after each retry
     * @param maxInterval     upper bound of the wait
     */
    public static MethodInterceptor interceptor(int maxAttempts, Duration initialInterval, double multiplier,
                                                Duration maxInterval) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        return RetryInterceptorBuilder.stateless()
                .maxRetries(maxAttempts - 1)
                .backOffOptions(initialInterval.toMillis(), multiplier, maxInterval.toMillis())
                .recoverer(new RejectAndDontRequeueRecoverer(() -> "Retries exhausted: message sent to the dead-letter queue"))
                .build();
    }
}
