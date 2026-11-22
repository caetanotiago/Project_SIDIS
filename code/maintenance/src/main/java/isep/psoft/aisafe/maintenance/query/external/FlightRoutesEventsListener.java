package isep.psoft.aisafe.maintenance.query.external;

import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FlightRoutesEventsListener {

    private final ExternalEventsProjection projection;

    @RabbitListener(id = "maintenance-flightroutes-events", queues = "#{flightRoutesEventsSubscription.queueName}",
            autoStartup = "false")
    public void on(IntegrationEvent<JsonNode> event) {
        projection.apply(event);
    }
}
