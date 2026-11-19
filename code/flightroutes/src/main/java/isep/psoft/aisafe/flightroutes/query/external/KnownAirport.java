package isep.psoft.aisafe.flightroutes.query.external;

import isep.psoft.aisafe.flightroutes.events.external.AirportEventPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local read model of an airport of the Airports service, built from its events (replaces B2 once enabled). */
@Entity
@Table(name = "KNOWN_AIRPORT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownAirport {

    @Id
    @Column(length = 3)
    private String iataCode;
    private Double latitude;
    private Double longitude;
    private String status;
    private Long sourceVersion;

    KnownAirport(String iataCode) {
        this.iataCode = iataCode;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    /** Fields that are null in the payload (e.g. a status-only event) keep their current value. */
    void apply(AirportEventPayload p) {
        if (p.latitude() != null) this.latitude = p.latitude();
        if (p.longitude() != null) this.longitude = p.longitude();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
    }
}
