package isep.psoft.aisafe.aircraftmanagement.query.external;

import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Consumes the route and scheduled-flight events of Flight Routes from THIS replica's queue
 * {@code <instanceId>.flightroutes} (e.g. {@code aircraftmanagement-2.flightroutes}, declared by
 * {@code AircraftManagementMessagingConfiguration}) and hands them to {@link FlightRoutesEventsProjection}.
 * Asynchronous inter-service communication: Flight Routes does not know this consumer exists.
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FlightRoutesEventsListener {

    private final FlightRoutesEventsProjection projection;

    @RabbitListener(id = "aircraftmanagement-flightroutes-events", queues = "#{flightRoutesEventsSubscription.queueName}")
    public void on(IntegrationEvent<JsonNode> event) {
        projection.apply(event);
    }
}
