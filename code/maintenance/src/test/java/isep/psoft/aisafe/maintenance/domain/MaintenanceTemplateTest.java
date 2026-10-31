package isep.psoft.aisafe.maintenance.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceTemplateTest {

    private static MaintenanceTemplate template(List<String> models, List<String> checklist, MaintenanceInterval interval) {
        return MaintenanceTemplate.create("A-Check", TemplateType.INSPECTION, models, checklist, interval);
    }

    @Test
    void createsTemplateWithNormalizedData() {
        MaintenanceTemplate t = template(List.of(" A320 ", "B737"), List.of(" Inspect tyres "), new MaintenanceInterval(500, 120));

        assertNotNull(t.getId());
        assertEquals(List.of("Inspect tyres"), t.getChecklist());
        assertTrue(t.appliesTo("a320"));
        assertFalse(t.appliesTo("E190"));
        assertTrue(t.isPeriodic());
    }

    @Test
    void requiresModelsAndChecklist() {
        assertThrows(IllegalArgumentException.class, () -> template(List.of(), List.of("x"), null));
        assertThrows(IllegalArgumentException.class, () -> template(List.of("A320"), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> template(List.of("A320"), List.of(" "), null));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceTemplate.create(" ", TemplateType.INSPECTION, List.of("A320"), List.of("x"), null));
    }

    @Test
    void intervalRules() {
        assertNull(MaintenanceInterval.ofOptional(null, null));
        assertFalse(template(List.of("A320"), List.of("x"), null).isPeriodic());
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceInterval(0, null));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceInterval(null, -1));
        assertEquals(30, new MaintenanceInterval(null, 30).getCalendarDays());
    }

    @Test
    void referenceTemplatesHaveTheSameIdInEveryReplica() {
        MaintenanceTemplate a = MaintenanceTemplate.reference("A-Check", TemplateType.INSPECTION, List.of("A320"), List.of("x"), null);
        MaintenanceTemplate b = MaintenanceTemplate.reference("a-check ", TemplateType.INSPECTION, List.of("A320"), List.of("x"), null);
        assertEquals(a.getId(), b.getId());
        assertEquals(MaintenanceTemplate.referenceId("A-Check"), a.getId());
    }

    @Test
    void parsesEnumsCaseInsensitivelyAndRejectsUnknownValues() {
        assertEquals(TemplateType.SCHEDULED_MAINTENANCE, TemplateType.parse("scheduled maintenance"));
        assertEquals(MaintenanceComponent.ENGINE, MaintenanceComponent.parse("engine"));
        assertNull(MaintenanceComponent.parseOptional(""));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MaintenanceComponent.parse("wings"));
        assertTrue(e.getMessage().contains("AIRFRAME"));
        assertEquals(CostGrouping.AIRCRAFT, CostGrouping.parse(null));
        assertEquals(CostGrouping.MODEL, CostGrouping.parse("model"));
    }
}
