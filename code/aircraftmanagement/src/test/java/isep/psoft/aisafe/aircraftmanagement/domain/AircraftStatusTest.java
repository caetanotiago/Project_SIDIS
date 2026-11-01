package isep.psoft.aisafe.aircraftmanagement.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa o parsing do estado de aeronave (valores do contrato §2.2). */
class AircraftStatusTest {

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "available", "Available", "  available  "})
    void parseIsCaseInsensitiveAndTrimsInput(String input) {
        assertEquals(AircraftStatus.AVAILABLE, AircraftStatus.parse(input));
    }

    @Test
    void parseAcceptsAllContractValues() {
        assertEquals(AircraftStatus.IN_FLIGHT, AircraftStatus.parse("IN_FLIGHT"));
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, AircraftStatus.parse("UNDER_MAINTENANCE"));
        assertEquals(AircraftStatus.INACTIVE, AircraftStatus.parse("INACTIVE"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parseRejectsNullOrBlank(String input) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AircraftStatus.parse(input));
        assertEquals("Aircraft status is required", ex.getMessage());
    }

    @Test
    void parseRejectsUnknownValueAndListsAllowedOnes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AircraftStatus.parse("FLYING"));
        assertTrue(ex.getMessage().contains("FLYING"));
        assertTrue(ex.getMessage().contains("AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE, INACTIVE"));
    }
}
