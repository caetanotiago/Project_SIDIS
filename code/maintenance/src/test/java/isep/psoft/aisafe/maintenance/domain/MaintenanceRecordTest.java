package isep.psoft.aisafe.maintenance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceRecordTest {

    private static final LocalDate START = LocalDate.now().minusDays(2);

    static MaintenanceRecord record() {
        return new MaintenanceRecord("CS-TUA", "A320",
                new TemplateReference("tpl-1", "A-Check", TemplateType.INSPECTION),
                List.of("Inspect tyres", "Check oil"),
                new RecordDetails("Routine A-check", START, 480),
                null);
    }

    static CompletionDetails completion(LocalDate date) {
        return new CompletionDetails(date, "No findings", new BigDecimal("1500.50"), 600, 1234.5);
    }

    @Test
    void newRecordIsInProgressWithTheTemplateChecklist() {
        MaintenanceRecord r = record();

        assertEquals(MaintenanceStatus.IN_PROGRESS, r.getStatus());
        assertTrue(r.isOngoing());
        assertEquals(List.of("Inspect tyres", "Check oil"), r.getChecklist());
        assertEquals("A-Check", r.getTemplate().getTemplateName());
        assertEquals(480, r.maintenanceMinutes());
    }

    @Test
    void recordDetailsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new RecordDetails("x", LocalDate.now().plusDays(1), 10));
        assertThrows(IllegalArgumentException.class, () -> new RecordDetails("x", START, 0));
        assertThrows(IllegalArgumentException.class, () -> new RecordDetails(" ", START, 10));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceRecord("CS-TUA", "A320",
                new TemplateReference("t", "n", TemplateType.INSPECTION), List.of(), new RecordDetails("x", START, 1), null));
    }

    @Test
    void completingStoresTheDetailsAndUsesTheActualDuration() {
        MaintenanceRecord r = record();
        r.complete(completion(LocalDate.now()));

        assertEquals(MaintenanceStatus.COMPLETED, r.getStatus());
        assertEquals(600, r.maintenanceMinutes());
        assertEquals(new BigDecimal("1500.50"), r.getCompletion().getCost());
    }

    @Test
    void aRecordIsCompletedOnlyOnce() {
        MaintenanceRecord r = record();
        r.complete(completion(LocalDate.now()));

        assertThrows(IllegalStateException.class, () -> r.complete(completion(LocalDate.now())));
    }

    @Test
    void completionDataIsValidated() {
        MaintenanceRecord r = record();
        assertThrows(IllegalArgumentException.class, () -> r.complete(completion(START.minusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> completion(LocalDate.now().plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionDetails(LocalDate.now(), "n", new BigDecimal("-1"), 10, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionDetails(LocalDate.now(), "n", BigDecimal.ONE, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionDetails(LocalDate.now(), " ", BigDecimal.ONE, 10, null));
        assertTrue(r.isOngoing());
    }

    @Test
    void categorisingIsOnlyAllowedWhileInProgress() {
        MaintenanceRecord r = record();
        r.categorize(MaintenanceComponent.ENGINE);
        assertEquals(MaintenanceComponent.ENGINE, r.getComponent());

        r.complete(completion(LocalDate.now()));
        assertThrows(IllegalStateException.class, () -> r.categorize(MaintenanceComponent.AVIONICS));
    }
}
