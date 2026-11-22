package isep.psoft.aisafe.airports.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Publica depois do commit
@Component
public class IntegrationEventRelay {
    private static final Logger log = LoggerFactory.getLogger(IntegrationEventRelay.class);

    private final ObjectProvider<IntegrationEventTransport> transports;

    // Construtor
    public IntegrationEventRelay(ObjectProvider<IntegrationEventTransport> transports) {
        this.transports = transports;
    }

    // Envia para o transporte
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OutgoingIntegrationEvent outgoing) {
        transports.orderedStream().forEach(transport -> {
            try {
                transport.send(outgoing);
            } catch (RuntimeException e) {
                log.error("{} {} do aeroporto {} foi gravado mas não foi publicado (evento perdido para as outras réplicas): {}",
                        outgoing.event().eventType(), outgoing.event().eventId(), outgoing.event().aggregateId(),
                        e.getMessage());
            }
        });
    }
}
