package isep.sidis.common.messaging;

import com.rabbitmq.client.Channel;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.support.ContainerUtils;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The listener advice: bounded attempts, then reject without requeue (= the broker dead-letters the message). */
class ConsumerRetryTest {

    @Test
    void aFailingListenerRunsMaxAttemptsTimesAndIsThenRejectedWithoutRequeue() {
        MethodInterceptor retry = ConsumerRetry.interceptor(3, Duration.ofMillis(1), 1.0, Duration.ofMillis(1));
        AtomicInteger attempts = new AtomicInteger();

        Throwable thrown = assertThrows(Exception.class, () -> retry.invoke(listenerCall(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("handler failed");
        })));

        assertEquals(3, attempts.get());
        assertGoesToTheDeadLetterQueue(thrown);
    }

    @Test
    void aListenerThatSucceedsOnTheSecondAttemptIsAcked() throws Throwable {
        MethodInterceptor retry = ConsumerRetry.interceptor(3, Duration.ofMillis(1), 1.0, Duration.ofMillis(1));
        AtomicInteger attempts = new AtomicInteger();

        retry.invoke(listenerCall(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
        }));

        assertEquals(2, attempts.get());
    }

    @Test
    void oneAttemptMeansNoRetry() {
        MethodInterceptor retry = ConsumerRetry.interceptor(1, Duration.ofMillis(1), 1.0, Duration.ofMillis(1));
        AtomicInteger attempts = new AtomicInteger();

        Throwable thrown = assertThrows(Exception.class, () -> retry.invoke(listenerCall(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("handler failed");
        })));

        assertEquals(1, attempts.get());
        assertGoesToTheDeadLetterQueue(thrown);
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerRetry.interceptor(0, Duration.ZERO, 1.0, Duration.ZERO));
    }

    /** The same decision the listener container takes: even with requeue on by default, this one is not requeued. */
    private static void assertGoesToTheDeadLetterQueue(Throwable thrown) {
        assertFalse(ContainerUtils.shouldRequeue(true, thrown, LogFactory.getLog(ConsumerRetryTest.class)),
                "retries exhausted: the message must be rejected without requeue, not " + thrown);
    }

    /** Same arguments the listener container passes to its advice chain: (channel, message). */
    private static MethodInvocation listenerCall(Runnable listener) {
        Object[] args = { mock(Channel.class), new Message("{}".getBytes(), new MessageProperties()) };
        return new MethodInvocation() {
            @Override public Method getMethod() {
                try {
                    return Runnable.class.getMethod("run");
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            }
            @Override public Object[] getArguments() { return args; }
            @Override public Object proceed() { listener.run(); return null; }
            @Override public Object getThis() { return listener; }
            @Override public AccessibleObject getStaticPart() { return getMethod(); }
        };
    }
}
