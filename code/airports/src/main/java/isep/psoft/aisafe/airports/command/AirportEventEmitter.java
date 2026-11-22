package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.query.AirportProjection;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class AirportEventEmitter {
    private final AirportProjection localReadModel;
    private final ApplicationEventPublisher springEvents;
    private final String instanceId;
    private final Clock clock;

    public AirportEventEmitter(AirportProjection localReadModel, ApplicationEventPublisher springEvents,
                               @Value("${aisafe.instance-id}") String instanceId) {
        this.localReadModel = localReadModel;
        this.springEvents = springEvents;
        this.instanceId = instanceId;
        this.clock = Clock.systemUTC();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AirportEventPayload> airportCreated(Airport airport) {
        return emit(AirportEvents.AIRPORT_CREATED, AirportEvents.CREATED_KEY, airport);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AirportEventPayload> airportUpdated(Airport airport) {
        return emit(AirportEvents.AIRPORT_UPDATED, AirportEvents.UPDATED_KEY, airport);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<AirportEventPayload> airportStatusChanged(Airport airport) {
        return emit(AirportEvents.AIRPORT_STATUS_CHANGED, AirportEvents.STATUS_CHANGED_KEY, airport);
    }

    private IntegrationEvent<AirportEventPayload> emit(String eventType, String routingKey, Airport airport) {
        IntegrationEvent<AirportEventPayload> event = IntegrationEvent.create(eventType, clock.instant(),
                AirportEvents.SOURCE_SERVICE, instanceId, airport.getIataCode(), AirportEventPayload.of(airport));
        localReadModel.apply(event);
        springEvents.publishEvent(new OutgoingIntegrationEvent(routingKey, event));
        return event;
    }
}
