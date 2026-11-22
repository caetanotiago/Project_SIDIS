package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.command.ReadModelSynchronizer;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftSyncRequest;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes THIS replica's queue {@code <instanceId>.aircraftmanagement} (declared by
 * {@code AircraftManagementMessagingConfiguration}). The queue carries several message types, so the payload is read
 * as a JSON tree:
 * <ul>
 *   <li>aircraft and model events → {@link AircraftManagementEventRouter} (read models);</li>
 *   <li>{@code AircraftSyncRequested} → {@link ReadModelSynchronizer} (republish the state this replica owns).</li>
 * </ul>
 * The message is acked only when this method returns; an exception makes the container retry (bounded, with
 * back-off) and then dead-letter it to {@code <queue>.dlq} (policy from the common library).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AircraftManagementEventsListener {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final AircraftManagementEventRouter router;
    private final ReadModelSynchronizer synchronizer;

    @RabbitListener(id = "aircraftmanagement-events", queues = "#{aircraftManagementEventsSubscription.queueName}")
    public void on(IntegrationEvent<JsonNode> event) {
        if (AircraftEvents.SYNC_REQUESTED.equals(event.eventType())) {
            AircraftSyncRequest request = event.payload() == null ? null
                    : JSON.convertValue(event.payload(), AircraftSyncRequest.class);
            synchronizer.onSyncRequested(request == null ? event.sourceInstance() : request.requestedBy());
            return;
        }
        router.apply(event);
    }
}
