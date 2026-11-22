package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEvents;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftManagementEventRouter;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The only way the command side announces a change ("write -> event -> projection on every replica"), exactly as
 * {@code FlightRoutesEventEmitter} in Flight Routes:
 * <ol>
 *   <li>builds ONE integration event (one eventId) with the full state of the entity after the change;</li>
 *   <li>applies it to THIS replica's read model inside the write transaction, so the replica that made the change
 *       reads its own write immediately, with or without a broker (read-your-writes on the owner);</li>
 *   <li>hands the same event to {@link IntegrationEventRelay}, which publishes it after the commit. When the broker
 *       delivers it back to this replica, its eventId is already recorded and it is skipped; every other replica
 *       (and every other service subscribed, e.g. Flight Routes) applies it to its own read model (eventual
 *       consistency).</li>
 * </ol>
 * Must be called inside the write transaction, after the entity was flushed (so its {@code @Version} is final).
 */
@Component
public class AircraftManagementEventEmitter {

    private final AircraftManagementEventRouter localReadModel;
    private final ApplicationEventPublisher springEvents;
    private final String instanceId;
    private final Clock clock;

    public AircraftManagementEventEmitter(AircraftManagementEventRouter localReadModel,
                                          ApplicationEventPublisher springEvents,
                                          @Value("${aisafe.instance-id}") String instanceId) {
        this.localReadModel = localReadModel;
        this.springEvents = springEvents;
        this.instanceId = instanceId;
        this.clock = Clock.systemUTC();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AircraftEventPayload> aircraftRegistered(Aircraft aircraft) {
        return emit(AircraftEvents.AIRCRAFT_REGISTERED, AircraftEvents.REGISTERED_KEY,
                aircraft.getRegistrationNumber(), AircraftEventPayload.of(aircraft));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AircraftEventPayload> aircraftStatusChanged(Aircraft aircraft) {
        return emit(AircraftEvents.AIRCRAFT_STATUS_CHANGED, AircraftEvents.STATUS_CHANGED_KEY,
                aircraft.getRegistrationNumber(), AircraftEventPayload.of(aircraft));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AircraftModelEventPayload> aircraftModelCreated(AircraftModel model) {
        return emit(AircraftModelEvents.AIRCRAFT_MODEL_CREATED, AircraftModelEvents.CREATED_KEY,
                model.getModelName(), AircraftModelEventPayload.of(model));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AircraftModelEventPayload> aircraftModelUpdated(AircraftModel model) {
        return emit(AircraftModelEvents.AIRCRAFT_MODEL_UPDATED, AircraftModelEvents.UPDATED_KEY,
                model.getModelName(), AircraftModelEventPayload.of(model));
    }

    private <T> IntegrationEvent<T> emit(String eventType, String routingKey, String aggregateId, T payload) {
        IntegrationEvent<T> event = IntegrationEvent.create(eventType, clock.instant(), AircraftEvents.SOURCE_SERVICE,
                instanceId, aggregateId, payload);
        localReadModel.apply(event);
        springEvents.publishEvent(new OutgoingIntegrationEvent(routingKey, event));
        return event;
    }
}
