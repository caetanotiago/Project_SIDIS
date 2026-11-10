package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes the route events from THIS replica's own queue ({@code flightroutes.<instanceId>.flightroutes}, declared
 * by {@code RouteMessagingConfiguration}) and applies them to the local read model.
 *
 * <p>The message is acked only when this method returns; an exception makes the container retry (bounded, with
 * back-off) and then dead-letter it to {@code <queue>.dlq} (policy from the common library).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RouteEventsListener {

    private final RouteProjection projection;

    @RabbitListener(id = "flightroutes-route-events", queues = "#{routeEventsSubscription.queueName}")
    public void on(IntegrationEvent<RouteEventPayload> event) {
        projection.apply(event);
    }
}
