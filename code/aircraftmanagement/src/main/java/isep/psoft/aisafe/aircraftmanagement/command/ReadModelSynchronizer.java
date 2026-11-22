package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftSyncRequest;
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

/**
 * Synchronisation of the read models when a replica STARTS (same design as {@code ReadModelSynchronizer} in
 * Airports). Solves "a queue only receives the events published after it was created" (new replica, erased
 * database, replicas that start at different times):
 * <ol>
 *   <li>republishes the models and aircraft this replica owns: its own read model is complete and the replicas (and
 *       services) already running receive them;</li>
 *   <li>publishes {@code AircraftSyncRequested}: the other replicas answer by republishing what they own, which
 *       reaches this replica's queue (it already exists: the listeners start before this event).</li>
 * </ol>
 * Runs on {@link ApplicationReadyEvent}, after the demo data ({@code DemoDataBootstrap}, {@code @Order(1)}).
 * Without a broker only step 1 runs.
 */
@Component
public class ReadModelSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ReadModelSynchronizer.class);

    private final AircraftStateRepublisher republisher;
    private final ObjectProvider<EventPublisher> eventPublisher;
    private final String instanceId;

    public ReadModelSynchronizer(AircraftStateRepublisher republisher, ObjectProvider<EventPublisher> eventPublisher,
                                 @Value("${aisafe.instance-id}") String instanceId) {
        this.republisher = republisher;
        this.eventPublisher = eventPublisher;
        this.instanceId = instanceId;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void onStartup() {
        republisher.republishOwnedState("startup of " + instanceId);
        eventPublisher.ifAvailable(publisher -> {
            try {
                publisher.publish(AircraftEvents.SYNC_REQUESTED, AircraftEvents.SYNC_REQUESTED_KEY, instanceId,
                        new AircraftSyncRequest(instanceId));
                log.info("Asked the other replicas to republish their aircraft and models ({})",
                        AircraftEvents.SYNC_REQUESTED);
            } catch (AmqpException e) {
                log.error("Could not ask the other replicas to republish their state: {}", e.getMessage());
            }
        });
    }

    /** Request of another replica (received from the broker). This replica's own request is ignored. */
    public void onSyncRequested(String requestedBy) {
        if (instanceId.equals(requestedBy)) {
            return;
        }
        republisher.republishOwnedState("sync requested by " + requestedBy);
    }
}
