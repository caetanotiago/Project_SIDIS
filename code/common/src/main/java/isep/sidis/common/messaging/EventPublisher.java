package isep.sidis.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

/**
 * Publishes {@link IntegrationEvent}s to this service's events exchange ({@code aisafe.<service>.events}).
 * The envelope metadata (id, timestamp, source service and instance) is filled in automatically.
 *
 * <pre>
 * eventPublisher.publish("RouteCreated", "route.created", route.getId(), payload);
 * </pre>
 *
 * <p>Messages are persistent (survive a broker restart in durable queues). When the connection factory
 * uses {@code spring.rabbitmq.publisher-confirm-type=simple}, {@code publish} waits until the broker
 * confirms the message and throws if it does not. Call it <b>after</b> the local transaction has committed
 * (e.g. from a {@code @TransactionalEventListener(phase = AFTER_COMMIT)}), so that no event announces a
 * change that was rolled back.
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final RabbitTemplate rabbitTemplate;
    private final String sourceService;
    private final String sourceInstance;
    private final String exchange;
    private final Clock clock;
    private final Duration confirmTimeout;

    public EventPublisher(RabbitTemplate rabbitTemplate, String sourceService, String sourceInstance,
                          Clock clock, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.sourceService = sourceService;
        this.sourceInstance = sourceInstance;
        this.exchange = MessagingNames.eventsExchange(sourceService);
        this.clock = clock;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * Wraps the payload in an envelope and publishes it.
     *
     * @param eventType   e.g. {@code RouteCreated}
     * @param routingKey  {@code <aggregate>.<action>}, e.g. {@code route.created} (see {@link MessagingNames#routingKey})
     * @param aggregateId id of the entity the event is about
     * @param payload     event data (serialised to JSON)
     * @return the envelope that was sent (its {@code eventId} identifies it in logs and in the broker)
     * @throws AmqpException if the broker cannot be reached or does not confirm the message
     */
    public <T> IntegrationEvent<T> publish(String eventType, String routingKey, String aggregateId, T payload) {
        IntegrationEvent<T> event = IntegrationEvent.create(eventType, clock.instant(), sourceService,
                sourceInstance, aggregateId, payload);
        MessagePostProcessor metadata = message -> {
            var props = message.getMessageProperties();
            props.setMessageId(event.eventId().toString());
            props.setType(event.eventType());
            props.setAppId(sourceService);
            props.setTimestamp(Date.from(event.timestamp()));
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            String correlationId = MDC.get(MDC_CORRELATION_ID);
            if (correlationId != null) {
                props.setHeader(MessagingNames.CORRELATION_ID_HEADER, correlationId);
            }
            return message;
        };

        if (rabbitTemplate.getConnectionFactory().isSimplePublisherConfirms()) {
            rabbitTemplate.invoke(ops -> {
                ops.convertAndSend(exchange, routingKey, event, metadata);
                ops.waitForConfirmsOrDie(confirmTimeout.toMillis());
                return null;
            });
        } else {
            rabbitTemplate.convertAndSend(exchange, routingKey, event, metadata);
        }
        log.info("Published {} {} for {} to {} ({})", event.eventType(), event.eventId(), aggregateId, exchange, routingKey);
        return event;
    }

    public String getExchange() {
        return exchange;
    }

    public String getSourceService() {
        return sourceService;
    }

    public String getSourceInstance() {
        return sourceInstance;
    }
}
