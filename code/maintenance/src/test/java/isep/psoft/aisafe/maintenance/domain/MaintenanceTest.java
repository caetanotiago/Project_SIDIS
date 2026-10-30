package isep.psoft.aisafe.maintenance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for maintenance aggregate invariants and state transitions.
 *
 * @author Diogo Fernandes - 1240632
 */
class MaintenanceTest {

    @Test
    void schedulingCreatesMaintenanceInScheduledState() {
        Maintenance maintenance = new Maintenance(
                "CS-TST",
                LocalDate.now().plusDays(1),
                "Inspection");

        assertEquals(MaintenanceStatus.SCHEDULED, maintenance.getStatus());
        assertEquals("CS-TST", maintenance.getAircraftRegistration());
    }

    @Test
    void schedulingRejectsDateThatIsNotInTheFuture() {
        assertThrows(IllegalArgumentException.class, () -> new Maintenance(
                "CS-TST",
                LocalDate.now(),
                "Inspection"));
    }

    @Test
    void completingScheduledMaintenanceStoresReportAndCompletionDate() {
        LocalDate scheduledDate = LocalDate.now().plusDays(1);
        LocalDate completionDate = scheduledDate.plusDays(2);
        Maintenance maintenance = new Maintenance("CS-TST", scheduledDate, "Inspection");

        maintenance.complete(new MaintenanceReport("Inspection completed"), completionDate);

        assertEquals(MaintenanceStatus.COMPLETED, maintenance.getStatus());
        assertEquals(completionDate, maintenance.getCompletionDate());
        assertEquals("Inspection completed", maintenance.getReport().getText());
    }

    @Test
    void completedMaintenanceCannotBeCompletedAgain() {
        LocalDate scheduledDate = LocalDate.now().plusDays(1);
        Maintenance maintenance = new Maintenance("CS-TST", scheduledDate, "Inspection");
        maintenance.complete(new MaintenanceReport("Inspection completed"), scheduledDate);

        assertThrows(IllegalStateException.class, () ->
                maintenance.complete(new MaintenanceReport("Second report"), scheduledDate));
    }
}
