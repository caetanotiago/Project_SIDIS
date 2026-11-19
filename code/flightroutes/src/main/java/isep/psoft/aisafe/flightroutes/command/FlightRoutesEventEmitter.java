package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEventPayload;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEvents;
import isep.psoft.aisafe.flightroutes.query.FlightRoutesEventRouter;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The only way the command side announces a change ("write -> event -> projection on every replica"):
 * <ol>
 *   <li>builds ONE integration event (one eventId) with the full state of the entity after the change;</li>
 *   <li>applies it to THIS replica's read model inside the write transaction, so the replica that made the change
 *       reads its own write immediately, with or without a broker (read-your-writes on the owner);</li>
 *   <li>hands the same event to {@link IntegrationEventRelay}, which publishes it after the commit. When the broker
 *       delivers it back to this replica, its eventId is already recorded and it is skipped; every other replica
 *       applies it to its own read model (eventual consistency).</li>
 * </ol>
 * Must be called inside the write transaction, after the entity was flushed (so its {@code @Version} is final).
 */
@Component
public class FlightRoutesEventEmitter {

    private final FlightRoutesEventRouter localReadModel;
    private final ApplicationEventPublisher springEvents;
    private final String instanceId;
    private final Clock clock;

    public FlightRoutesEventEmitter(FlightRoutesEventRouter localReadModel, ApplicationEventPublisher springEvents,
                                    @Value("${aisafe.instance-id}") String instanceId) {
        this.localReadModel = localReadModel;
        this.springEvents = springEvents;
        this.instanceId = instanceId;
        this.clock = Clock.systemUTC();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<RouteEventPayload> routeCreated(FlightRoute route) {
        return emit(RouteEvents.ROUTE_CREATED, RouteEvents.CREATED_KEY, route.getId(), RouteEventPayload.of(route));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<RouteEventPayload> routeUpdated(FlightRoute route) {
        return emit(RouteEvents.ROUTE_UPDATED, RouteEvents.UPDATED_KEY, route.getId(), RouteEventPayload.of(route));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<ScheduledFlightEventPayload> scheduledFlightCreated(ScheduledFlight flight) {
        return emit(ScheduledFlightEvents.SCHEDULED_FLIGHT_CREATED, ScheduledFlightEvents.CREATED_KEY, flight.getId(),
                ScheduledFlightEventPayload.of(flight));
    }

    private <T> IntegrationEvent<T> emit(String eventType, String routingKey, String aggregateId, T payload) {
        IntegrationEvent<T> event = IntegrationEvent.create(eventType, clock.instant(), RouteEvents.SOURCE_SERVICE,
                instanceId, aggregateId, payload);
        localReadModel.apply(event);
        springEvents.publishEvent(new OutgoingIntegrationEvent(routingKey, event));
        return event;
    }
}
