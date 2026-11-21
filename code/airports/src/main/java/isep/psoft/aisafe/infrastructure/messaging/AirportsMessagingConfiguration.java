package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do broker desta réplica do Airports (só com {@code aisafe.messaging.enabled=true}). O exchange
 * {@code aisafe.airports.events}, para onde este serviço publica, já é declarado pela biblioteca common.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class AirportsMessagingConfiguration {

    /**
     * Fila {@code <instanceId>.airports} (ex. {@code airports-2.airports}) + a sua DLQ, ligada a {@code airport.*} e a
     * {@code airport-sync.requested}: cada réplica recebe TODOS os eventos de aeroportos, incluindo os seus, para
     * construir o read model {@code REPLICATED_AIRPORT}, e os pedidos de sincronização das outras réplicas
     * ({@code AirportsEventsListener}).
     */
    @Bean
    public EventSubscription airportEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(AirportEvents.SOURCE_SERVICE, AirportEvents.ALL_AIRPORT_EVENTS,
                AirportEvents.SYNC_REQUESTED_KEY);
    }
}
