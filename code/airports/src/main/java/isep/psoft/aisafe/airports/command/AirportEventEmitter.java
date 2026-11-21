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

/**
 * Única forma de o lado de escrita anunciar uma alteração a um aeroporto:
 * <ol>
 *   <li>constrói UM evento de integração (um eventId) com o estado completo do aeroporto depois da alteração;</li>
 *   <li>aplica-o ao read model DESTA réplica dentro da transação de escrita, para a réplica que fez a alteração a
 *       ler logo a seguir, com ou sem broker (read-your-writes na réplica dona);</li>
 *   <li>entrega-o ao {@link IntegrationEventRelay}, que o publica depois do commit. Todas as réplicas do Airports
 *       (e os outros serviços subscritos, ex. Flight Routes) recebem-no na sua própria fila. Quando volta a esta
 *       réplica, o eventId já está registado e é ignorado; as outras aplicam-no ao seu read model (consistência
 *       eventual).</li>
 * </ol>
 * Tem de ser chamado dentro da transação de escrita, depois do flush (para que o {@code @Version} seja o final).
 * Só a réplica DONA do aeroporto chama este emitter: as outras reencaminham o comando para ela, por isso os eventos
 * de um aeroporto têm um único produtor e saem por ordem.
 */
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
