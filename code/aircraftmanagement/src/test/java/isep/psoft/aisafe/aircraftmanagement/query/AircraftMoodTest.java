package isep.psoft.aisafe.aircraftmanagement.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The mood rule of /api/v2/unicorns (docs/2-containers/Unicorns-Mock.md): a pure function of the status. */
class AircraftMoodTest {

    @Test
    void moodFollowsTheContract() {
        assertEquals("HAPPY", AircraftMood.moodOf("AVAILABLE"));
        assertEquals("BUSY", AircraftMood.moodOf("IN_FLIGHT"));
        assertEquals("GROUNDED_AND_GRUMPY", AircraftMood.moodOf("UNDER_MAINTENANCE"));
        assertEquals("NEUTRAL", AircraftMood.moodOf("INACTIVE"));
        assertEquals("NEUTRAL", AircraftMood.moodOf(null));
    }
}
