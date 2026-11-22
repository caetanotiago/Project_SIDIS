package isep.psoft.aisafe.airports.query.external;

import isep.psoft.aisafe.airports.events.external.FlightRouteEventPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "KNOWN_ROUTE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownRoute {
    @Id
    private String routeId;

    @Column(length = 3)
    private String originIata;

    @Column(length = 3)
    private String destinationIata;

    private String status;

    private Long sourceVersion;

    KnownRoute(String routeId) {
        this.routeId = routeId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(FlightRouteEventPayload p) {
        if (p.originIata() != null) this.originIata = p.originIata().toUpperCase();
        if (p.destinationIata() != null) this.destinationIata = p.destinationIata().toUpperCase();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
    }
}
