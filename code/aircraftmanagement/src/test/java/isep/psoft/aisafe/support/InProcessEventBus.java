package isep.psoft.aisafe.support;

import isep.psoft.aisafe.AircraftManagementApplication;
import isep.psoft.aisafe.aircraftmanagement.command.IntegrationEventTransport;
import isep.psoft.aisafe.aircraftmanagement.command.OutgoingIntegrationEvent;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftManagementEventRouter;
import isep.psoft.aisafe.aircraftmanagement.query.external.FlightRoutesEventsProjection;
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
 * Test stand-in for RabbitMQ (as in Flight Routes): an {@link IntegrationEventTransport} shared by several Aircraft
 * Management replicas started in the same JVM. Like one queue per replica, every committed event is delivered to EVERY
 * running replica (including the one that published it, which must skip it as a duplicate). The event goes through
 * JSON exactly as on the broker, so the payload reaches the consumers as a JSON tree and the event contract is
 * exercised. {@link #deliverFromFlightRoutes} plays the role of the {@code <instanceId>.flightroutes} queues.
 */
public final class InProcessEventBus implements IntegrationEventTransport {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();
    private static final TypeReference<IntegrationEvent<JsonNode>> ENVELOPE = new TypeReference<>() { };

    private final Map<String, ConfigurableApplicationContext> replicas = new ConcurrentHashMap<>();
    private final Set<String> disconnected = ConcurrentHashMap.newKeySet();
    private final List<IntegrationEvent<JsonNode>> delivered = new CopyOnWriteArrayList<>();

    /** Starts a replica (command-line style args) with this bus registered as its event transport. */
    public ConfigurableApplicationContext start(String name, String... args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AircraftManagementApplication.class)
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

    /** Events published by the Aircraft Management replicas, as they went over the wire. */
    public List<IntegrationEvent<JsonNode>> delivered() {
        return List.copyOf(delivered);
    }

    @Override
    public void send(OutgoingIntegrationEvent outgoing) {
        IntegrationEvent<JsonNode> overTheWire = overTheWire(outgoing.event());
        delivered.add(overTheWire);
        replicas.forEach((name, ctx) -> {
            if (ctx.isActive() && !disconnected.contains(name)) {
                ctx.getBean(AircraftManagementEventRouter.class).apply(overTheWire);
            }
        });
    }

    /** An event of Flight Routes delivered to every running replica (its queue {@code <instanceId>.flightroutes}). */
    public void deliverFromFlightRoutes(IntegrationEvent<?> event) {
        IntegrationEvent<JsonNode> overTheWire = overTheWire(event);
        replicas.forEach((name, ctx) -> {
            if (ctx.isActive() && !disconnected.contains(name)) {
                ctx.getBean(FlightRoutesEventsProjection.class).apply(overTheWire);
            }
        });
    }

    private static IntegrationEvent<JsonNode> overTheWire(IntegrationEvent<?> event) {
        return JSON.readValue(JSON.writeValueAsString(event), ENVELOPE);
    }
}
