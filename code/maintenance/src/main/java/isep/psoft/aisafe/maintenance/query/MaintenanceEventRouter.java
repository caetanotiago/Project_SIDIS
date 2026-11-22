package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import isep.psoft.aisafe.maintenance.events.MaintenanceTemplateEventPayload;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MaintenanceEventRouter {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceEventRouter.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final MaintenanceTemplateProjection templates;
    private final MaintenanceRecordProjection records;

    public MaintenanceEventRouter(MaintenanceTemplateProjection templates, MaintenanceRecordProjection records) {
        this.templates = templates;
        this.records = records;
    }

    public boolean apply(IntegrationEvent<?> event) {
        return switch (event.eventType()) {
            case MaintenanceEvents.TEMPLATE_CREATED ->
                    templates.apply(withPayload(event, MaintenanceTemplateEventPayload.class));
            case MaintenanceEvents.RECORD_CREATED, MaintenanceEvents.RECORD_CATEGORIZED,
                 MaintenanceEvents.RECORD_COMPLETED ->
                    records.apply(withPayload(event, MaintenanceRecordEventPayload.class));
            default -> {
                log.warn("Ignoring unknown event type {} ({}) from {}", event.eventType(), event.eventId(),
                        event.sourceInstance());
                yield false;
            }
        };
    }

    static <T> IntegrationEvent<T> withPayload(IntegrationEvent<?> event, Class<T> type) {
        Object raw = event.payload();
        T payload = raw == null || type.isInstance(raw) ? type.cast(raw) : JSON.convertValue(raw, type);
        return new IntegrationEvent<>(event.eventId(), event.eventType(), event.timestamp(), event.sourceService(),
                event.sourceInstance(), event.aggregateId(), payload);
    }
}
