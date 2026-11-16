package isep.sidis.common.messaging;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

import java.time.Duration;

public final class ConsumerRetry {
    private ConsumerRetry() {
    }

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
