package isep.psoft.aisafe.aircraftmanagement.query.external;

import isep.psoft.aisafe.aircraftmanagement.events.external.RoutePayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Local read model of a route of Flight Routes (table {@code KNOWN_ROUTE}), built from its {@code RouteCreated} /
 * {@code RouteUpdated} events. Answers US203 (compatible routes) without calling Flight Routes (replaces bridge B6).
 */
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

    private Double distance;
    private Double minRange;
    private Integer minCapacity;
    private Integer estimatedFlightTime;
    private String status;

    private Long sourceVersion;
    private Instant lastEventAt;

    KnownRoute(String routeId) {
        this.routeId = routeId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(RoutePayload p, Instant eventAt) {
        if (p.originIata() != null) this.originIata = p.originIata();
        if (p.destinationIata() != null) this.destinationIata = p.destinationIata();
        if (p.distance() != null) this.distance = p.distance();
        if (p.minRange() != null) this.minRange = p.minRange();
        if (p.minCapacity() != null) this.minCapacity = p.minCapacity();
        if (p.estimatedFlightTime() != null) this.estimatedFlightTime = p.estimatedFlightTime();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
        this.lastEventAt = eventAt;
    }
}
