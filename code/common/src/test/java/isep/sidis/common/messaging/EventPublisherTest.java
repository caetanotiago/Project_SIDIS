package isep.sidis.common.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The publisher fills the envelope and the AMQP message metadata (no broker: RabbitTemplate is mocked). */
class EventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-10T14:00:00Z");

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final EventPublisher publisher = new EventPublisher(template, "flightroutes", "flightroutes-2",
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(5));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void publishesTheEnvelopeToTheServiceExchangeWithTheRoutingKey() {
        ConnectionFactory cf = mock(ConnectionFactory.class);
        when(template.getConnectionFactory()).thenReturn(cf);
        MDC.put("correlationId", "corr-42");

        IntegrationEvent<String> event = publisher.publish("RouteCreated", "route.created", "r-1", "payload");

        assertEquals("aisafe.flightroutes.events", publisher.getExchange());
        assertEquals("RouteCreated", event.eventType());
        assertEquals(NOW, event.timestamp());
        assertEquals("flightroutes", event.sourceService());
        assertEquals("flightroutes-2", event.sourceInstance());
        assertEquals("r-1", event.aggregateId());
        assertEquals("payload", event.payload());

        ArgumentCaptor<MessagePostProcessor> metadata = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(eq("aisafe.flightroutes.events"), eq("route.created"), eq(event), metadata.capture());

        Message message = metadata.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        MessageProperties props = message.getMessageProperties();
        assertEquals(event.eventId().toString(), props.getMessageId());
        assertEquals("RouteCreated", props.getType());
        assertEquals("flightroutes", props.getAppId());
        assertEquals(MessageDeliveryMode.PERSISTENT, props.getDeliveryMode());
        assertEquals("corr-42", props.getHeader(MessagingNames.CORRELATION_ID_HEADER));
    }

    @Test
    void withSimplePublisherConfirmsItWaitsForTheBrokerConfirm() {
        ConnectionFactory cf = mock(ConnectionFactory.class);
        when(cf.isSimplePublisherConfirms()).thenReturn(true);
        when(template.getConnectionFactory()).thenReturn(cf);

        publisher.publish("RouteUpdated", "route.updated", "r-1", "payload");

        verify(template).invoke(any());
        verify(template, never()).convertAndSend(any(String.class), any(String.class), any(Object.class),
                any(MessagePostProcessor.class));
    }
}
