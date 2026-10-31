package isep.psoft.aisafe.maintenance.domain;

import java.util.Arrays;

/** Conversão tolerante (maiúsculas/minúsculas, espaços) de texto para enums, com 400 explícito. */
final class Enums {

    private Enums() {
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + description + " is required.");
        }
        String normalized = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value
                    + ". Allowed values: " + Arrays.toString(type.getEnumConstants()));
        }
    }
}
