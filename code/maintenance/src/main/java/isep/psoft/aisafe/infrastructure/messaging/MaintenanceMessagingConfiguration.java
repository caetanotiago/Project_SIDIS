package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.external.AircraftManagementEvents;
import isep.psoft.aisafe.maintenance.events.external.FlightRoutesEvents;
import isep.sidis.common.messaging.EventSubscription;
import isep.sidis.common.messaging.MessagingTopology;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class MaintenanceMessagingConfiguration {

    @Bean
    public EventSubscription maintenanceEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(MaintenanceEvents.SOURCE_SERVICE, MaintenanceEvents.ALL_TEMPLATE_EVENTS,
                MaintenanceEvents.ALL_RECORD_EVENTS, MaintenanceEvents.SYNC_REQUESTED_KEY);
    }

    @Bean
    public EventSubscription aircraftManagementEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(AircraftManagementEvents.SOURCE_SERVICE, AircraftManagementEvents.ALL_AIRCRAFT_EVENTS,
                AircraftManagementEvents.ALL_AIRCRAFT_MODEL_EVENTS);
    }

    @Bean
    public EventSubscription flightRoutesEventsSubscription(MessagingTopology topology) {
        return topology.subscribe(FlightRoutesEvents.SOURCE_SERVICE, FlightRoutesEvents.ALL_SCHEDULED_FLIGHT_EVENTS);
    }
}
