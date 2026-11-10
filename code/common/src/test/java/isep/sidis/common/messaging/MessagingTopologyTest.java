package isep.sidis.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Naming rules and the broker objects declared for one consuming instance. */
class MessagingTopologyTest {

    @Test
    void namesFollowTheConvention() {
        assertEquals("aisafe.flightroutes.events", MessagingNames.eventsExchange("flightroutes"));
        assertEquals("route.created", MessagingNames.routingKey("route", "created"));
        assertEquals("flightroutes.flightroutes-2.flightroutes",
                MessagingNames.consumerQueue("flightroutes", "flightroutes-2", "flightroutes"));
        assertEquals("q.dlq", MessagingNames.deadLetterQueue("q"));
    }

    @Test
    void namePartsMustBeLowerCaseWithoutDots() {
        assertThrows(IllegalArgumentException.class, () -> MessagingNames.eventsExchange("Flight.Routes"));
        assertThrows(IllegalArgumentException.class, () -> MessagingNames.routingKey("route", ""));
        assertThrows(IllegalArgumentException.class, () -> MessagingNames.consumerQueue("a", null, "b"));
    }

    @Test
    void subscriptionDeclaresADurableQueuePerInstanceWithItsDeadLetterQueue() {
        EventSubscription sub = new MessagingTopology("maintenance", "maintenance-1")
                .subscribe("flightroutes", "route.*", "flight.created");

        assertEquals("maintenance.maintenance-1.flightroutes", sub.getQueueName());
        assertEquals("maintenance.maintenance-1.flightroutes.dlq", sub.getDeadLetterQueueName());
        assertEquals("aisafe.flightroutes.events", sub.getSourceExchange());

        TopicExchange exchange = sub.getDeclarablesByType(TopicExchange.class).getFirst();
        assertEquals("aisafe.flightroutes.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());

        DirectExchange dlx = sub.getDeclarablesByType(DirectExchange.class).getFirst();
        assertEquals(MessagingNames.DEAD_LETTER_EXCHANGE, dlx.getName());

        Map<String, Queue> queues = sub.<Queue>getDeclarablesByType(Queue.class).stream()
                .collect(java.util.stream.Collectors.toMap(Queue::getName, q -> q));
        Queue queue = queues.get("maintenance.maintenance-1.flightroutes");
        assertTrue(queue.isDurable());
        assertFalse(queue.isExclusive());
        assertFalse(queue.isAutoDelete());
        assertEquals("aisafe.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("maintenance.maintenance-1.flightroutes", queue.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(queues.get("maintenance.maintenance-1.flightroutes.dlq").isDurable());

        List<String> bindings = sub.<Binding>getDeclarablesByType(Binding.class).stream()
                .map(b -> b.getExchange() + " -[" + b.getRoutingKey() + "]-> " + b.getDestination())
                .sorted().toList();
        assertEquals(List.of(
                "aisafe.dlx -[maintenance.maintenance-1.flightroutes]-> maintenance.maintenance-1.flightroutes.dlq",
                "aisafe.flightroutes.events -[flight.created]-> maintenance.maintenance-1.flightroutes",
                "aisafe.flightroutes.events -[route.*]-> maintenance.maintenance-1.flightroutes"), bindings);
    }

    @Test
    void aSubscriptionNeedsAtLeastOneRoutingKeyPattern() {
        MessagingTopology topology = new MessagingTopology("airports", "airports-1");
        assertThrows(IllegalArgumentException.class, () -> topology.subscribe("flightroutes"));
    }
}
