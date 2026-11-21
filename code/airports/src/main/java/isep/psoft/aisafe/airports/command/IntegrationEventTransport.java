package isep.psoft.aisafe.airports.command;

/**
 * Envia um evento já confirmado (commit) às outras réplicas/serviços. Em produção: RabbitMQ
 * ({@code RabbitIntegrationEventTransport}, só com {@code aisafe.messaging.enabled=true}). Os testes podem registar
 * um transporte em memória que entrega o evento a outras réplicas arrancadas na mesma JVM.
 * Sem transporte (mensagens desligadas), o evento só atualiza o read model desta réplica.
 */
public interface IntegrationEventTransport {

    void send(OutgoingIntegrationEvent outgoing);
}
