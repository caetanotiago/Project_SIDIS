package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.command.ReadModelSynchronizer;
import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceSyncRequest;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MaintenanceEventsListener {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final MaintenanceEventRouter router;
    private final ReadModelSynchronizer synchronizer;

    @RabbitListener(id = "maintenance-events", queues = "#{maintenanceEventsSubscription.queueName}",
            autoStartup = "false")
    public void on(IntegrationEvent<JsonNode> event) {
        if (MaintenanceEvents.SYNC_REQUESTED.equals(event.eventType())) {
            MaintenanceSyncRequest request = event.payload() == null ? null
                    : JSON.convertValue(event.payload(), MaintenanceSyncRequest.class);
            synchronizer.onSyncRequested(request == null ? event.sourceInstance() : request.requestedBy());
            return;
        }
        router.apply(event);
    }
}
