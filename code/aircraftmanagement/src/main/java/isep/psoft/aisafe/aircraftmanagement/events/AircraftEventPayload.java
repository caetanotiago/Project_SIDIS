package isep.psoft.aisafe.aircraftmanagement.events;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;

import java.time.LocalDate;

/**
 * Payload of {@code AircraftRegistered} and {@code AircraftStatusChanged}: the full state of the aircraft after the
 * change ("state-carrying" event), so a consumer never has to call Aircraft Management back. The fields agreed in
 * Integration-Contracts section 9.4 ({@code registrationNumber}, {@code modelName}, {@code seatingCapacity},
 * {@code status}, {@code version}) are always filled; {@code manufacturingDate} is an extra field that tolerant
 * readers (Flight Routes) ignore.
 *
 * <pre>
 * { "registrationNumber": "CS-TUA", "modelName": "A320", "seatingCapacity": 180,
 *   "manufacturingDate": "2019-05-14", "status": "AVAILABLE", "version": 0 }
 * </pre>
 *
 * @param status  AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE or INACTIVE
 * @param version the aircraft's optimistic-lock version on its owner replica: it grows with every change, so a
 *                consumer can ignore an event older than what it already has.
 */
public record AircraftEventPayload(
        String registrationNumber,
        String modelName,
        Integer seatingCapacity,
        LocalDate manufacturingDate,
        String status,
        Long version) {

    /** The state of an aircraft of the write model, after it was flushed (so {@code version} is final). */
    public static AircraftEventPayload of(Aircraft aircraft) {
        return new AircraftEventPayload(aircraft.getRegistrationNumber(), aircraft.getModelName(),
                aircraft.getSeatingCapacity(), aircraft.getManufacturingDate(), aircraft.getStatus().name(),
                aircraft.getVersion());
    }
}
