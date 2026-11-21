package isep.psoft.aisafe.airports.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica os eventos do lado de escrita DEPOIS do commit da transação local.
 *
 * <p>Limitação conhecida (sem transactional outbox): se o transporte falhar nesse momento (ex.: broker em baixo),
 * a alteração já está gravada e visível nesta réplica, mas o evento perde-se para as outras. Fica registado como
 * erro no log e o pedido HTTP termina com sucesso na mesma.
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
                log.error("{} {} do aeroporto {} foi gravado mas não foi publicado (evento perdido para as outras réplicas): {}",
                        outgoing.event().eventType(), outgoing.event().eventId(), outgoing.event().aggregateId(),
                        e.getMessage());
            }
        });
    }
}
