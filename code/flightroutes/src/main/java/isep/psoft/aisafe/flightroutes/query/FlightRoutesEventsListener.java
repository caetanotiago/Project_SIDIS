package isep.psoft.aisafe.flightroutes.query;

import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Consumes Flight Routes' own events (routes and scheduled flights) from THIS replica's queue
 * {@code <instanceId>.flightroutes} (declared by {@code FlightRoutesMessagingConfiguration}) and hands them to
 * {@link FlightRoutesEventRouter}. The payload is read as a JSON tree because the queue carries two payload types.
 *
 * <p>The message is acked only when this method returns; an exception makes the container retry (bounded, with
 * back-off) and then dead-letter it to {@code <queue>.dlq} (policy from the common library).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FlightRoutesEventsListener {

    private final FlightRoutesEventRouter router;

    @RabbitListener(id = "flightroutes-events", queues = "#{flightRoutesEventsSubscription.queueName}")
    public void on(IntegrationEvent<JsonNode> event) {
        router.apply(event);
    }
}
