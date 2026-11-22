package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import isep.psoft.aisafe.maintenance.events.MaintenanceTemplateEventPayload;
import isep.psoft.aisafe.maintenance.query.MaintenanceEventRouter;
import isep.sidis.common.messaging.IntegrationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class MaintenanceEventEmitter {

    private final MaintenanceEventRouter localReadModel;
    private final ApplicationEventPublisher springEvents;
    private final String instanceId;
    private final Clock clock = Clock.systemUTC();

    public MaintenanceEventEmitter(MaintenanceEventRouter localReadModel, ApplicationEventPublisher springEvents,
                                   @Value("${aisafe.instance-id}") String instanceId) {
        this.localReadModel = localReadModel;
        this.springEvents = springEvents;
        this.instanceId = instanceId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<MaintenanceTemplateEventPayload> templateCreated(MaintenanceTemplate template) {
        return emit(MaintenanceEvents.TEMPLATE_CREATED, MaintenanceEvents.TEMPLATE_CREATED_KEY, template.getId(),
                MaintenanceTemplateEventPayload.of(template));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<MaintenanceRecordEventPayload> recordCreated(MaintenanceRecord record) {
        return emit(MaintenanceEvents.RECORD_CREATED, MaintenanceEvents.RECORD_CREATED_KEY, record.getId(),
                MaintenanceRecordEventPayload.of(record));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<MaintenanceRecordEventPayload> recordCategorized(MaintenanceRecord record) {
        return emit(MaintenanceEvents.RECORD_CATEGORIZED, MaintenanceEvents.RECORD_CATEGORIZED_KEY, record.getId(),
                MaintenanceRecordEventPayload.of(record));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationEvent<MaintenanceRecordEventPayload> recordCompleted(MaintenanceRecord record) {
        return emit(MaintenanceEvents.RECORD_COMPLETED, MaintenanceEvents.RECORD_COMPLETED_KEY, record.getId(),
                MaintenanceRecordEventPayload.of(record));
    }

    private <T> IntegrationEvent<T> emit(String eventType, String routingKey, String aggregateId, T payload) {
        IntegrationEvent<T> event = IntegrationEvent.create(eventType, clock.instant(), MaintenanceEvents.SOURCE_SERVICE,
                instanceId, aggregateId, payload);
        localReadModel.apply(event);
        springEvents.publishEvent(new OutgoingIntegrationEvent(routingKey, event));
        return event;
    }
}
