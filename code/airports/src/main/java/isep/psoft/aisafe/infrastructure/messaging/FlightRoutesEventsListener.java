package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.events.external.FlightRouteEventPayload;
import isep.psoft.aisafe.airports.query.external.RouteCountProjection;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FlightRoutesEventsListener {
    private final RouteCountProjection projection;

    @RabbitListener(id = "airports-flightroutes-events", queues = "#{flightRoutesEventsSubscription.queueName}")
    public void on(IntegrationEvent<FlightRouteEventPayload> event) {
        projection.apply(event);
    }
}
