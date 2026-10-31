package isep.psoft.aisafe.aircraftmanagement.domain;

/** Valores definidos em Integration-Contracts §2.2 (viajam como strings em maiúsculas). */
public enum AircraftStatus {
    AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE, INACTIVE;

    public static AircraftStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Aircraft status is required");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid aircraft status: " + value
                    + ". Allowed: AVAILABLE, IN_FLIGHT, UNDER_MAINTENANCE, INACTIVE");
        }
    }
}
