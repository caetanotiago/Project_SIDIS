package isep.psoft.aisafe.airports.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código IATA de aeroporto: exatamente 3 letras (PSOFT §9 "IATA airport codes (3-letter format)").
 * É a identidade do agregado Airport e é também a forma como os outros microsserviços o referenciam
 * (Flight Routes guarda só o IATA da origem/destino). Normalizado sempre para maiúsculas.
 */
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
