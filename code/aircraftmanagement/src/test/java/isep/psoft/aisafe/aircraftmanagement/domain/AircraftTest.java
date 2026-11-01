package isep.psoft.aisafe.aircraftmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes unitários da entidade Aircraft (US102 / US105). */
class AircraftTest {

    private static final LocalDate MANUFACTURED = LocalDate.of(2020, 5, 17);

    private static Aircraft aircraft(AircraftStatus status) {
        return new Aircraft("CS-TUA", "A320", 180, MANUFACTURED, status);
    }

    @Nested
    class Creation {

        @Test
        void createsAircraftWithGivenValues() {
            Aircraft a = aircraft(AircraftStatus.UNDER_MAINTENANCE);

            assertEquals("CS-TUA", a.getRegistrationNumber());
            assertEquals("A320", a.getModelName());
            assertEquals(180, a.getSeatingCapacity());
            assertEquals(MANUFACTURED, a.getManufacturingDate());
            assertEquals(AircraftStatus.UNDER_MAINTENANCE, a.getStatus());
        }

        @Test
        void nullStatusDefaultsToAvailable() {
            assertEquals(AircraftStatus.AVAILABLE, aircraft(null).getStatus());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void rejectsBlankRegistrationNumber(String registration) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new Aircraft(registration, "A320", 180, MANUFACTURED, null));
            assertEquals("Registration number cannot be empty", ex.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void rejectsBlankModelName(String modelName) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new Aircraft("CS-TUA", modelName, 180, MANUFACTURED, null));
            assertEquals("Model name cannot be empty", ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -180})
        void rejectsNonPositiveSeatingCapacity(int seats) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new Aircraft("CS-TUA", "A320", seats, MANUFACTURED, null));
            assertEquals("Seating capacity must be positive", ex.getMessage());
        }

        @Test
        void keepsRegistrationNumberExactlyAsReceived() {
            Aircraft a = new Aircraft("cs-tua", "A320", 180, MANUFACTURED, null);
            assertEquals("cs-tua", a.getRegistrationNumber());
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void sameStatusIsNoOp() {
            Aircraft a = aircraft(AircraftStatus.IN_FLIGHT);

            a.changeStatus(AircraftStatus.IN_FLIGHT);

            assertEquals(AircraftStatus.IN_FLIGHT, a.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = AircraftStatus.class, names = {"IN_FLIGHT", "AVAILABLE"}, mode = EnumSource.Mode.EXCLUDE)
        void availableCanChangeToAnyOtherStatus(AircraftStatus target) {
            Aircraft a = aircraft(AircraftStatus.AVAILABLE);

            a.changeStatus(target);

            assertEquals(target, a.getStatus());
        }

        @Test
        void availableCanChangeToInFlight() {
            Aircraft a = aircraft(AircraftStatus.AVAILABLE);

            a.changeStatus(AircraftStatus.IN_FLIGHT);

            assertEquals(AircraftStatus.IN_FLIGHT, a.getStatus());
        }

        @Test
        void inFlightCanReturnToAvailable() {
            Aircraft a = aircraft(AircraftStatus.IN_FLIGHT);

            a.changeStatus(AircraftStatus.AVAILABLE);

            assertEquals(AircraftStatus.AVAILABLE, a.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = AircraftStatus.class, names = {"UNDER_MAINTENANCE", "INACTIVE"})
        void inFlightCannotChangeToMaintenanceOrInactive(AircraftStatus target) {
            Aircraft a = aircraft(AircraftStatus.IN_FLIGHT);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> a.changeStatus(target));

            assertEquals("Aircraft CS-TUA is in flight and cannot change to " + target, ex.getMessage());
            assertEquals(AircraftStatus.IN_FLIGHT, a.getStatus()); // estado não é alterado
        }

        @ParameterizedTest
        @EnumSource(value = AircraftStatus.class, names = {"UNDER_MAINTENANCE", "INACTIVE"})
        void maintenanceAndInactiveCanChangeToAnyStatus(AircraftStatus from) {
            for (AircraftStatus target : AircraftStatus.values()) {
                Aircraft a = aircraft(from);
                a.changeStatus(target);
                assertEquals(target, a.getStatus());
            }
        }
    }

    @Test
    void toSummaryCopiesAllFields() {
        AircraftSummary s = aircraft(AircraftStatus.INACTIVE).toSummary();

        assertEquals(new AircraftSummary("CS-TUA", "A320", 180, MANUFACTURED, AircraftStatus.INACTIVE), s);
    }
}
