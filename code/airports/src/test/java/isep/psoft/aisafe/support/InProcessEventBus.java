package isep.psoft.aisafe.support;

import isep.psoft.aisafe.AirportsApplication;
import isep.psoft.aisafe.airports.command.IntegrationEventTransport;
import isep.psoft.aisafe.airports.command.OutgoingIntegrationEvent;
import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.psoft.aisafe.airports.query.AirportProjection;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// RabbitMQ falso (testes)
public final class InProcessEventBus implements IntegrationEventTransport {
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();
    private static final TypeReference<IntegrationEvent<AirportEventPayload>> ENVELOPE = new TypeReference<>() { };

    private final Map<String, ConfigurableApplicationContext> replicas = new ConcurrentHashMap<>();
    private final Set<String> disconnected = ConcurrentHashMap.newKeySet();
    private final List<IntegrationEvent<AirportEventPayload>> delivered = new CopyOnWriteArrayList<>();

    // Arranca réplica com bus
    public ConfigurableApplicationContext start(String name, String... args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AirportsApplication.class)
                .initializers(c -> c.getBeanFactory().registerSingleton("inProcessEventBus", this))
                .run(args);
        replicas.put(name, ctx);
        return ctx;
    }

    // Simula partição
    public void disconnect(String name) {
        disconnected.add(name);
    }

    // Volta a ligar
    public void reconnect(String name) {
        disconnected.remove(name);
    }

    // Eventos entregues
    public List<IntegrationEvent<AirportEventPayload>> delivered() {
        return List.copyOf(delivered);
    }

    // Entrega a todas as réplicas
    @Override
    public void send(OutgoingIntegrationEvent outgoing) {
        IntegrationEvent<AirportEventPayload> overTheWire =
                JSON.readValue(JSON.writeValueAsString(outgoing.event()), ENVELOPE);
        delivered.add(overTheWire);
        replicas.forEach((name, ctx) -> {
            if (ctx.isActive() && !disconnected.contains(name)) {
                ctx.getBean(AirportProjection.class).apply(overTheWire);
            }
        });
    }
}
