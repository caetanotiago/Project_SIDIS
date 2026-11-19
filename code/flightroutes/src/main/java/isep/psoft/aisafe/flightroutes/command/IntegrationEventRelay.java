package isep.psoft.aisafe.flightroutes.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes the integration events of the command side AFTER the local transaction has committed.
 *
 * <p>Known limitation (no transactional outbox): if a transport fails at that moment (e.g. the broker is down), the
 * change is already committed and visible on this replica, but the event is lost for the others. It is logged as an
 * error and the HTTP request still succeeds. See docs/2-containers/Containers.md, section 3.6.
 */
@Component
public class IntegrationEventRelay {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventRelay.class);

    private final ObjectProvider<IntegrationEventTransport> transports;

    public IntegrationEventRelay(ObjectProvider<IntegrationEventTransport> transports) {
        this.transports = transports;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OutgoingIntegrationEvent outgoing) {
        transports.orderedStream().forEach(transport -> {
            try {
                transport.send(outgoing);
            } catch (RuntimeException e) {
                log.error("{} {} for {} was committed but could not be published (event lost for the other replicas): {}",
                        outgoing.event().eventType(), outgoing.event().eventId(), outgoing.event().aggregateId(),
                        e.getMessage());
            }
        });
    }
}
