package isep.psoft.aisafe.flightroutes.query.external;

import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Consumes Aircraft Management and Airports events from this replica's queues {@code <instanceId>.aircraftmanagement}
 * and {@code <instanceId>.airports}. Only when messaging is on AND
 * {@code aisafe.flightroutes.external-events.enabled=true}.
 */
@Component
@ConditionalOnExpression("${aisafe.messaging.enabled:false} and ${aisafe.flightroutes.external-events.enabled:false}")
@RequiredArgsConstructor
public class ExternalEventsListener {

    private final ExternalEventsProjection projection;

    @RabbitListener(id = "flightroutes-aircraft-events", queues = "#{aircraftEventsSubscription.queueName}")
    public void onAircraftEvent(IntegrationEvent<JsonNode> event) {
        projection.apply(event);
    }

    @RabbitListener(id = "flightroutes-airport-events", queues = "#{airportEventsSubscription.queueName}")
    public void onAirportEvent(IntegrationEvent<JsonNode> event) {
        projection.apply(event);
    }
}
