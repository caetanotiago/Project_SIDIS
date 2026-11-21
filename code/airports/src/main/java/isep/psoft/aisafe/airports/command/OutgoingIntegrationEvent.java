package isep.psoft.aisafe.airports.command;

import isep.sidis.common.messaging.IntegrationEvent;

/**
 * Evento Spring (em memória) levantado pelo lado de escrita DENTRO da transação. O {@link IntegrationEventRelay}
 * só o entrega aos transportes (RabbitMQ) DEPOIS do commit, para que o broker nunca anuncie uma alteração que
 * acabou por ser desfeita (rollback).
 */
public record OutgoingIntegrationEvent(String routingKey, IntegrationEvent<?> event) {
}
