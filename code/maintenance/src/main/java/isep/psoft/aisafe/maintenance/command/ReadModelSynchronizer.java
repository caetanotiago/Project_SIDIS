package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceSyncRequest;
import isep.sidis.common.messaging.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class ReadModelSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ReadModelSynchronizer.class);

    private final MaintenanceStateRepublisher republisher;
    private final ObjectProvider<EventPublisher> eventPublisher;
    private final String instanceId;

    public ReadModelSynchronizer(MaintenanceStateRepublisher republisher, ObjectProvider<EventPublisher> eventPublisher,
                                 @Value("${aisafe.instance-id}") String instanceId) {
        this.republisher = republisher;
        this.eventPublisher = eventPublisher;
        this.instanceId = instanceId;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void onStartup() {
        try {
            republisher.republishOwnedState("startup of " + instanceId);
        } catch (RuntimeException e) {
            log.error("Could not republish the state of {} at startup (it is republished again on the next sync "
                    + "request): {}", instanceId, e.getMessage());
        }
        eventPublisher.ifAvailable(publisher -> {
            try {
                publisher.publish(MaintenanceEvents.SYNC_REQUESTED, MaintenanceEvents.SYNC_REQUESTED_KEY, instanceId,
                        new MaintenanceSyncRequest(instanceId));
                log.info("Asked the other replicas to republish their templates and records ({})",
                        MaintenanceEvents.SYNC_REQUESTED);
            } catch (AmqpException e) {
                log.error("Could not ask the other replicas to republish their state: {}", e.getMessage());
            }
        });
    }

    public void onSyncRequested(String requestedBy) {
        if (instanceId.equals(requestedBy)) {
            return;
        }
        republisher.republishOwnedState("sync requested by " + requestedBy);
    }
}
