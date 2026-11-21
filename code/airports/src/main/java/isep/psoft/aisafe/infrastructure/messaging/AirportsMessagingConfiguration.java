package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.events.external.FlightRoutesEvents;
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

    /**
     * Fila {@code <instanceId>.flightroutes} (ex. {@code airports-1.flightroutes}) + DLQ, ligada a {@code route.*}
     * do exchange {@code aisafe.flightroutes.events}: alimenta o read model {@code KNOWN_ROUTE} (US210), em vez da
     * chamada HTTP B5 ao Flight Routes. O produtor não muda: só acrescentamos a nossa fila ao exchange dele.
     */
    @Bean
    public EventSubscription flightRoutesEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(FlightRoutesEvents.SOURCE_SERVICE, FlightRoutesEvents.ALL_ROUTE_EVENTS);
    }
}
