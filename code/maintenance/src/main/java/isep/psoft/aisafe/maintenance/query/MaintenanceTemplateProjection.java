package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceTemplateEventPayload;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceTemplateProjection {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTemplateProjection.class);

    private final ReplicatedMaintenanceTemplateRepository templates;
    private final IdempotentEventHandler idempotent;

    public MaintenanceTemplateProjection(ReplicatedMaintenanceTemplateRepository templates,
                                         ProcessedEventStore processedEvents) {
        this.templates = templates;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    @Transactional
    public boolean apply(IntegrationEvent<MaintenanceTemplateEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<MaintenanceTemplateEventPayload> event) {
        MaintenanceTemplateEventPayload p = event.payload();
        if (p == null || p.templateId() == null || p.templateId().isBlank()) {
            throw new IllegalArgumentException("Template event " + event.eventId() + " has no templateId");
        }
        if (!MaintenanceEvents.TEMPLATE_CREATED.equals(event.eventType())) {
            log.warn("Ignoring unknown template event type {} ({})", event.eventType(), event.eventId());
            return;
        }
        ReplicatedMaintenanceTemplate row = templates.findById(p.templateId())
                .orElseGet(() -> new ReplicatedMaintenanceTemplate(p.templateId()));
        if (row.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for template {} (version {} < stored {})", event.eventType(),
                    p.templateId(), p.version(), row.getSourceVersion());
            return;
        }
        row.apply(p, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        templates.save(row);
        log.info("Applied {} for template '{}' (owner {})", event.eventType(), p.name(), event.sourceInstance());
    }
}
