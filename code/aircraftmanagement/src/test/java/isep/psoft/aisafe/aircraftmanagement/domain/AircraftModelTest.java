package isep.psoft.aisafe.aircraftmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes unitários da entidade AircraftModel (US101 / US201). */
class AircraftModelTest {

    private static AircraftModel model() {
        return new AircraftModel("A320", "Airbus", 180, 24210.0, 6150.0, 840.0);
    }

    @Nested
    class Creation {

        @Test
        void createsModelWithGivenValues() {
            AircraftModel m = model();

            assertEquals("A320", m.getModelName());
            assertEquals("Airbus", m.getManufacturer());
            assertEquals(180, m.getSeatingCapacity());
            assertEquals(24210.0, m.getFuelCapacity());
            assertEquals(6150.0, m.getMaximumRange());
            assertEquals(840.0, m.getCruisingSpeed());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void rejectsBlankModelName(String name) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new AircraftModel(name, "Airbus", 180, 1.0, 1.0, 1.0));
            assertEquals("Model name cannot be empty", ex.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void rejectsBlankManufacturer(String manufacturer) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new AircraftModel("A320", manufacturer, 180, 1.0, 1.0, 1.0));
            assertEquals("Manufacturer cannot be empty", ex.getMessage());
        }

        @Test
        void rejectsNonPositiveSeatingCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new AircraftModel("A320", "Airbus", 0, 1.0, 1.0, 1.0));
        }

        @Test
        void rejectsNonPositiveFuelCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new AircraftModel("A320", "Airbus", 180, 0.0, 1.0, 1.0));
        }

        @Test
        void rejectsNonPositiveMaximumRange() {
            assertThrows(IllegalArgumentException.class, () -> new AircraftModel("A320", "Airbus", 180, 1.0, -5.0, 1.0));
        }

        @Test
        void rejectsNonPositiveCruisingSpeed() {
            assertThrows(IllegalArgumentException.class, () -> new AircraftModel("A320", "Airbus", 180, 1.0, 1.0, 0.0));
        }
    }

    @Nested
    class UpdateSpecifications {

        @Test
        void updatesOnlyProvidedFields() {
            AircraftModel m = model();

            m.updateSpecifications(null, null, null, 7000.0, null);

            assertEquals(7000.0, m.getMaximumRange());
            assertEquals("Airbus", m.getManufacturer());
            assertEquals(180, m.getSeatingCapacity());
            assertEquals(24210.0, m.getFuelCapacity());
            assertEquals(840.0, m.getCruisingSpeed());
        }

        @Test
        void updatesAllFieldsAndKeepsNameImmutable() {
            AircraftModel m = model();

            m.updateSpecifications("Airbus SE", 200, 25000.0, 6500.0, 850.0);

            assertEquals("A320", m.getModelName());
            assertEquals("Airbus SE", m.getManufacturer());
            assertEquals(200, m.getSeatingCapacity());
            assertEquals(25000.0, m.getFuelCapacity());
            assertEquals(6500.0, m.getMaximumRange());
            assertEquals(850.0, m.getCruisingSpeed());
        }

        @Test
        void allNullIsNoOp() {
            AircraftModel m = model();

            m.updateSpecifications(null, null, null, null, null);

            assertEquals(model().toSummary(), m.toSummary());
        }

        @Test
        void rejectsBlankManufacturer() {
            AircraftModel m = model();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> m.updateSpecifications("  ", null, null, null, null));

            assertEquals("Manufacturer cannot be empty", ex.getMessage());
        }

        @Test
        void invalidValueLeavesModelUnchanged() {
            AircraftModel m = model();

            // 1.º campo válido, 2.º inválido: nada pode ser aplicado (validação antes da escrita)
            assertThrows(IllegalArgumentException.class,
                    () -> m.updateSpecifications("Boeing", -1, null, null, null));

            assertEquals("Airbus", m.getManufacturer());
            assertEquals(180, m.getSeatingCapacity());
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -1.0})
        void rejectsNonPositiveNumericFields(double invalid) {
            AircraftModel m = model();

            assertThrows(IllegalArgumentException.class, () -> m.updateSpecifications(null, null, invalid, null, null));
            assertThrows(IllegalArgumentException.class, () -> m.updateSpecifications(null, null, null, invalid, null));
            assertThrows(IllegalArgumentException.class, () -> m.updateSpecifications(null, null, null, null, invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> m.updateSpecifications(null, (int) invalid, null, null, null));
        }
    }

    @Test
    void toSummaryCopiesAllFields() {
        assertEquals(new AircraftModelSummary("A320", "Airbus", 180, 24210.0, 6150.0, 840.0),
                model().toSummary());
    }
}
