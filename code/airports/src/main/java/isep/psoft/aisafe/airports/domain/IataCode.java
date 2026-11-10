package isep.psoft.aisafe.airports.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class IataCode {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}$");

    private IataCode() {
    }

    /** Normaliza (trim + maiúsculas) e valida; formato inválido → IllegalArgumentException (400). */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IATA code is required.");
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid IATA code: " + value + " (expected 3 letters).");
        }
        return code;
    }
}
