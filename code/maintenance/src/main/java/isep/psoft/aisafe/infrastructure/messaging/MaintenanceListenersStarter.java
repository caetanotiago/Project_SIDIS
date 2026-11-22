package isep.psoft.aisafe.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class MaintenanceListenersStarter {

    static final List<String> LISTENERS = List.of("maintenance-events", "maintenance-aircraftmanagement-events",
            "maintenance-flightroutes-events");

    private static final Logger log = LoggerFactory.getLogger(MaintenanceListenersStarter.class);

    private final RabbitListenerEndpointRegistry registry;

    public MaintenanceListenersStarter(RabbitListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void startAfterLocalData() {
        for (String id : LISTENERS) {
            MessageListenerContainer container = registry.getListenerContainer(id);
            if (container != null && !container.isRunning()) {
                container.start();
            }
        }
        log.info("Event listeners started after the reference and demo data were loaded: {}", LISTENERS);
    }
}
