package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology of this Flight Routes replica (only with {@code aisafe.messaging.enabled=true}). The common
 * library already declares the exchange {@code aisafe.flightroutes.events} that this service publishes to.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class RouteMessagingConfiguration {

    /**
     * Queue {@code flightroutes.<instanceId>.flightroutes} (+ its DLQ) bound to {@code route.*}: every replica gets
     * every route event, including its own, to build its read model ({@code RouteEventsListener}).
     */
    @Bean
    public EventSubscription routeEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(RouteEvents.SOURCE_SERVICE, RouteEvents.ALL_ROUTE_EVENTS);
    }
}
