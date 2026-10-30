package isep.psoft.aisafe.airports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value object: posição geográfica em graus decimais. Usada pelo Flight Routes para calcular a
 * distância das rotas (Haversine), por isso latitude e longitude são obrigatórias (contrato B2).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinates {

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public Coordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
