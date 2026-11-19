package isep.psoft.aisafe.support;

import isep.psoft.aisafe.FlightRoutesApplication;
import isep.psoft.aisafe.flightroutes.command.IntegrationEventTransport;
import isep.psoft.aisafe.flightroutes.command.OutgoingIntegrationEvent;
import isep.psoft.aisafe.flightroutes.query.FlightRoutesEventRouter;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test stand-in for RabbitMQ: an {@link IntegrationEventTransport} shared by several Flight Routes replicas started in
 * the same JVM. Like a fanout of one queue per replica, every committed event is delivered to EVERY running replica
 * (including the one that published it, which must skip it as a duplicate). The event goes through JSON exactly as on
 * the broker, so the payload reaches the consumers as a JSON tree and the event contract is exercised.
 *
 * <p>{@link #disconnect(String)} simulates a replica whose queue does not receive events (e.g. created later).
 */
public final class InProcessEventBus implements IntegrationEventTransport {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();
    private static final TypeReference<IntegrationEvent<JsonNode>> ENVELOPE = new TypeReference<>() { };

    private final Map<String, ConfigurableApplicationContext> replicas = new ConcurrentHashMap<>();
    private final Set<String> disconnected = ConcurrentHashMap.newKeySet();
    private final List<IntegrationEvent<JsonNode>> delivered = new CopyOnWriteArrayList<>();

    /** Starts a replica (command-line style args) with this bus registered as its event transport. */
    public ConfigurableApplicationContext start(String name, String... args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(FlightRoutesApplication.class)
                .initializers(c -> c.getBeanFactory().registerSingleton("inProcessEventBus", this))
                .run(args);
        replicas.put(name, ctx);
        return ctx;
    }

    public void disconnect(String name) {
        disconnected.add(name);
    }

    public void reconnect(String name) {
        disconnected.remove(name);
    }

    public List<IntegrationEvent<JsonNode>> delivered() {
        return List.copyOf(delivered);
    }

    @Override
    public void send(OutgoingIntegrationEvent outgoing) {
        IntegrationEvent<JsonNode> overTheWire = JSON.readValue(JSON.writeValueAsString(outgoing.event()), ENVELOPE);
        delivered.add(overTheWire);
        replicas.forEach((name, ctx) -> {
            if (ctx.isActive() && !disconnected.contains(name)) {
                ctx.getBean(FlightRoutesEventRouter.class).apply(overTheWire);
            }
        });
    }
}
