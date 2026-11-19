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

    /** Builds a new envelope (fresh eventId, now) and publishes it. */
    public <T> IntegrationEvent<T> publish(String eventType, String routingKey, String aggregateId, T payload) {
        IntegrationEvent<T> event = IntegrationEvent.create(eventType, clock.instant(), sourceService,
                sourceInstance, aggregateId, payload);
        return publish(event, routingKey);
    }

    /**
     * Publishes an envelope built earlier (e.g. applied to the producer's own read model inside the write
     * transaction and published after the commit), keeping its eventId so consumers can deduplicate it.
     * It must come from this service: its sourceService is the exchange it goes to.
     */
    public <T> IntegrationEvent<T> publish(IntegrationEvent<T> event, String routingKey) {
        if (!sourceService.equals(event.sourceService())) {
            throw new IllegalArgumentException("Event from " + event.sourceService() + " cannot be published by "
                    + sourceService + " (exchange " + exchange + ")");
        }
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
        log.info("Published {} {} for {} to {} ({})", event.eventType(), event.eventId(), event.aggregateId(), exchange, routingKey);
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
