package isep.psoft.aisafe.aircraftmanagement.dto;

import java.time.Instant;

/**
 * One item of {@code GET /api/v2/unicorns} (contract in docs/2-containers/Unicorns-Mock.md, identical to the Postman
 * mock): the mood of an aircraft, derived from its operational status.
 *
 * <pre>
 * { "registration": "CS-TUA", "model": "A320", "operationalStatus": "AVAILABLE", "mood": "HAPPY",
 *   "timestamp": "2026-11-18T09:15:00Z", "source": "read-model" }
 * </pre>
 *
 * @param timestamp when the read model last applied a status event for this aircraft
 * @param source    always {@code "read-model"}: the answer comes from the query side
 */
public record AircraftMoodDTO(String registration, String model, String operationalStatus, String mood,
                              Instant timestamp, String source) {

    public static final String SOURCE_READ_MODEL = "read-model";
}
