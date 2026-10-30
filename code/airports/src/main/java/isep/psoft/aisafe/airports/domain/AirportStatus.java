package isep.psoft.aisafe.airports.domain;

import java.util.Locale;

/**
 * Estado operacional de um aeroporto (US109: operational, closed, under maintenance).
 * Viaja em JSON como string em maiúsculas. Flight Routes só considera operacional o valor exato
 * "OPERATIONAL" (contrato B2).
 */
public enum AirportStatus {
    OPERATIONAL,
    CLOSED,
    UNDER_MAINTENANCE;

    public static AirportStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Airport status is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown airport status: " + value
                    + ". Allowed: OPERATIONAL, CLOSED, UNDER_MAINTENANCE.");
        }
    }
}
