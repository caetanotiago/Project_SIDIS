package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class MaintenanceRecordProjection {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRecordProjection.class);
    private static final Set<String> RECORD_EVENTS = Set.of(MaintenanceEvents.RECORD_CREATED,
            MaintenanceEvents.RECORD_CATEGORIZED, MaintenanceEvents.RECORD_COMPLETED);

    private final ReplicatedMaintenanceRecordRepository records;
    private final IdempotentEventHandler idempotent;

    public MaintenanceRecordProjection(ReplicatedMaintenanceRecordRepository records,
                                       ProcessedEventStore processedEvents) {
        this.records = records;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    @Transactional
    public boolean apply(IntegrationEvent<MaintenanceRecordEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<MaintenanceRecordEventPayload> event) {
        MaintenanceRecordEventPayload p = event.payload();
        if (p == null || p.recordId() == null || p.recordId().isBlank()) {
            throw new IllegalArgumentException("Record event " + event.eventId() + " has no recordId");
        }
        if (!RECORD_EVENTS.contains(event.eventType())) {
            log.warn("Ignoring unknown record event type {} ({})", event.eventType(), event.eventId());
            return;
        }
        ReplicatedMaintenanceRecord row = records.findById(p.recordId())
                .orElseGet(() -> new ReplicatedMaintenanceRecord(p.recordId()));
        if (row.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for record {} (version {} < stored {})", event.eventType(),
                    p.recordId(), p.version(), row.getSourceVersion());
            return;
        }
        row.apply(p, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        records.save(row);
        log.info("Applied {} for record {} ({} {}, status {}, owner {}, version {})", event.eventType(),
                p.recordId(), p.aircraftRegistration(), p.templateName(), p.status(), event.sourceInstance(),
                p.version());
    }
}
