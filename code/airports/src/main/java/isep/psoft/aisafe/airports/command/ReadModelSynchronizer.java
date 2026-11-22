package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.events.AirportSyncRequest;
import isep.sidis.common.messaging.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ReadModelSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(ReadModelSynchronizer.class);

    private final AirportStateRepublisher republisher;
    private final ObjectProvider<EventPublisher> eventPublisher;
    private final String instanceId;

    public ReadModelSynchronizer(AirportStateRepublisher republisher, ObjectProvider<EventPublisher> eventPublisher,
                                 @Value("${aisafe.instance-id}") String instanceId) {
        this.republisher = republisher;
        this.eventPublisher = eventPublisher;
        this.instanceId = instanceId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        republisher.republishOwnedAirports("startup of " + instanceId);
        eventPublisher.ifAvailable(publisher -> {
            try {
                publisher.publish(AirportEvents.SYNC_REQUESTED, AirportEvents.SYNC_REQUESTED_KEY, instanceId,
                        new AirportSyncRequest(instanceId));
                log.info("Asked the other replicas to republish their airports ({})", AirportEvents.SYNC_REQUESTED);
            } catch (AmqpException e) {
                log.error("Could not ask the other replicas to republish their airports: {}", e.getMessage());
            }
        });
    }

    public void onSyncRequested(String requestedBy) {
        if (instanceId.equals(requestedBy)) {
            return;
        }
        republisher.republishOwnedAirports("sync requested by " + requestedBy);
    }
}
