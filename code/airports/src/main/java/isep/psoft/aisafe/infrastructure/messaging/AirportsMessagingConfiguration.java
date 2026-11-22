package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.events.external.FlightRoutesEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Filas e bindings RabbitMQ
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class AirportsMessagingConfiguration {
    // Fila de eventos de aeroportos
    @Bean
    public EventSubscription airportEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(AirportEvents.SOURCE_SERVICE, AirportEvents.ALL_AIRPORT_EVENTS,
                AirportEvents.SYNC_REQUESTED_KEY);
    }

    // Fila de eventos de rotas
    @Bean
    public EventSubscription flightRoutesEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(FlightRoutesEvents.SOURCE_SERVICE, FlightRoutesEvents.ALL_ROUTE_EVENTS);
    }
}
