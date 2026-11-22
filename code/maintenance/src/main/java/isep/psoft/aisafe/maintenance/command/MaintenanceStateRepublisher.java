package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceStateRepublisher {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceStateRepublisher.class);

    private final MaintenanceTemplateRepository templates;
    private final MaintenanceRecordRepository records;
    private final MaintenanceEventEmitter events;

    @Transactional
    public int republishOwnedState(String reason) {
        List<MaintenanceTemplate> ownedTemplates = templates.findAll();
        List<MaintenanceRecord> ownedRecords = records.findAll();
        ownedTemplates.forEach(events::templateCreated);
        ownedRecords.forEach(r -> {
            if (r.isOngoing()) {
                events.recordCreated(r);
            } else {
                events.recordCompleted(r);
            }
        });
        log.info("Republished the state of {} owned template(s) and {} owned record(s) ({})",
                ownedTemplates.size(), ownedRecords.size(), reason);
        return ownedTemplates.size() + ownedRecords.size();
    }
}
