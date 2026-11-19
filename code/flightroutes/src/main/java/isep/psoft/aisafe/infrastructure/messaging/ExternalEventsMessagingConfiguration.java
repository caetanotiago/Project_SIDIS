package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.flightroutes.events.external.ExternalEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues of this replica for the events of Aircraft Management and Airports (consumer-side scaffolding, OFF by
 * default). Declaring a subscription also declares the producer's exchange, so this works even before the producer
 * has started: the queue simply stays empty until the first event.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${aisafe.messaging.enabled:false} and ${aisafe.flightroutes.external-events.enabled:false}")
public class ExternalEventsMessagingConfiguration {

    /** {@code <instanceId>.aircraftmanagement} bound to {@code aircraft.*} and {@code aircraft-model.*}. */
    @Bean
    public EventSubscription aircraftEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(ExternalEvents.AIRCRAFT_SERVICE, "aircraft.*", "aircraft-model.*");
    }

    /** {@code <instanceId>.airports} bound to {@code airport.*}. */
    @Bean
    public EventSubscription airportEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(ExternalEvents.AIRPORTS_SERVICE, "airport.*");
    }
}
