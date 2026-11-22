package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEvents;
import isep.psoft.aisafe.aircraftmanagement.events.external.FlightRoutesEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology of this Aircraft Management replica (only with {@code aisafe.messaging.enabled=true}). The common
 * library already declares the exchange {@code aisafe.aircraftmanagement.events} that this service publishes to.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class AircraftManagementMessagingConfiguration {

    /**
     * Queue {@code <instanceId>.aircraftmanagement} (+ its DLQ) bound to {@code aircraft.*}, {@code aircraft-model.*}
     * and {@code aircraft-sync.requested}: every replica gets every event of Aircraft Management, including its own,
     * to build its read models, and the synchronisation requests of the other replicas
     * ({@code AircraftManagementEventsListener}).
     */
    @Bean
    public EventSubscription aircraftManagementEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(AircraftEvents.SOURCE_SERVICE, AircraftEvents.ALL_AIRCRAFT_EVENTS,
                AircraftModelEvents.ALL_AIRCRAFT_MODEL_EVENTS, AircraftEvents.SYNC_REQUESTED_KEY);
    }

    /**
     * Queue {@code <instanceId>.flightroutes} (+ its DLQ) bound to {@code route.*} and {@code scheduled-flight.*} of
     * {@code aisafe.flightroutes.events}: the data of US203, US204 and US206 ({@code FlightRoutesEventsListener}).
     * Declaring it also declares Flight Routes' exchange, so this works even if Flight Routes starts later.
     */
    @Bean
    public EventSubscription flightRoutesEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(FlightRoutesEvents.SOURCE_SERVICE, FlightRoutesEvents.ALL_ROUTE_EVENTS,
                FlightRoutesEvents.ALL_SCHEDULED_FLIGHT_EVENTS);
    }
}
