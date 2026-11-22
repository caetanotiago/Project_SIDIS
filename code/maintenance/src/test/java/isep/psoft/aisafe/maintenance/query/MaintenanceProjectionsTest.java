package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceEvents;
import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.InMemoryProcessedEventStore;
import isep.sidis.common.messaging.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceProjectionsTest {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    @Mock ReplicatedMaintenanceRecordRepository records;
    @Mock ReplicatedMaintenanceTemplateRepository templates;

    private MaintenanceEventRouter router;

    @BeforeEach
    void setUp() {
        InMemoryProcessedEventStore processed = new InMemoryProcessedEventStore();
        router = new MaintenanceEventRouter(new MaintenanceTemplateProjection(templates, processed),
                new MaintenanceRecordProjection(records, processed));
    }

    private static MaintenanceRecordEventPayload payload(String status, long version) {
        return new MaintenanceRecordEventPayload("rec-1", "CS-TUA", "A320", "tpl", "A-Check", "INSPECTION", "d",
                LocalDate.now(), 60, List.of("x"), "ENGINE", status,
                "COMPLETED".equals(status) ? LocalDate.now() : null, null,
                "COMPLETED".equals(status) ? new BigDecimal("10") : null,
                "COMPLETED".equals(status) ? 50 : null, null, version);
    }

    private static IntegrationEvent<JsonNode> overTheWire(String type, MaintenanceRecordEventPayload p) {
        IntegrationEvent<MaintenanceRecordEventPayload> event = IntegrationEvent.create(type, Instant.now(),
                MaintenanceEvents.SOURCE_SERVICE, "maintenance-2", p.recordId(), p);
        return JSON.readValue(JSON.writeValueAsString(event), new TypeReference<IntegrationEvent<JsonNode>>() { });
    }

    @Test
    void anEventFromTheBrokerIsProjectedWithItsOwner() {
        when(records.findById("rec-1")).thenReturn(Optional.empty());

        assertTrue(router.apply(overTheWire(MaintenanceEvents.RECORD_CREATED, payload("IN_PROGRESS", 0))));

        ArgumentCaptor<ReplicatedMaintenanceRecord> saved = ArgumentCaptor.forClass(ReplicatedMaintenanceRecord.class);
        verify(records).save(saved.capture());
        assertEquals("IN_PROGRESS", saved.getValue().getStatus());
        assertEquals("maintenance-2", saved.getValue().getOwnerInstance());
        assertEquals(List.of("x"), saved.getValue().getChecklist());
    }

    @Test
    void theSameEventDeliveredTwiceIsAppliedOnce() {
        when(records.findById("rec-1")).thenReturn(Optional.empty());
        IntegrationEvent<JsonNode> event = overTheWire(MaintenanceEvents.RECORD_CREATED, payload("IN_PROGRESS", 0));

        assertTrue(router.apply(event));
        assertFalse(router.apply(event));
        verify(records, times(1)).save(any());
    }

    @Test
    void anOlderVersionIsIgnored() {
        ReplicatedMaintenanceRecord stored = ReadModelFixtures.completed("rec-1", "CS-TUA", "A320", "tpl", "10", 50,
                LocalDate.now(), null);
        when(records.findById("rec-1")).thenReturn(Optional.of(stored));

        router.apply(overTheWire(MaintenanceEvents.RECORD_CREATED, payload("IN_PROGRESS", 0)));

        verify(records, never()).save(any());
        assertEquals("COMPLETED", stored.getStatus());
    }

    @Test
    void unknownEventTypesAreIgnored() {
        assertFalse(router.apply(overTheWire("MaintenanceRecordArchived", payload("IN_PROGRESS", 0))));
        verifyNoInteractions(records, templates);
    }
}
