package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Read-side copy of a route, built ONLY from route events (table {@code REPLICATED_ROUTE} in this replica's own
 * database). Every replica ends up with every route here, whichever replica stored it (eventual consistency).
 *
 * <p>It is deliberately a separate table from the write model ({@code FlightRoute}): the write model stays
 * partitioned by owner replica, so the existing HTTP forwarding, the ownership rule and the summed statistics
 * (B5 count-by-airport) keep working unchanged. This table is the seed of the CQRS read model.
 */
@Entity
@Table(name = "REPLICATED_ROUTE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedRoute {

    @Id
    private String id;

    @Column(length = 3)
    private String originIata;

    @Column(length = 3)
    private String destinationIata;

    private Double distance;
    private Double minRange;
    private Integer minCapacity;
    private Integer estimatedFlightTime;
    private String status;

    /** Version of the route on its owner replica (from the event), NOT a local optimistic lock. */
    private Long sourceVersion;

    /** Replica that owns (stores and updates) the route: the {@code sourceInstance} of its events. */
    private String ownerInstance;

    /** Id and creation time of the last event applied to this row. */
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedRoute(String id) {
        this.id = id;
    }

    /** {@code true} if an event with this source version must be ignored because this row is already newer. */
    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(RouteEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        this.originIata = p.originIata();
        this.destinationIata = p.destinationIata();
        this.distance = p.distance();
        this.minRange = p.minRange();
        this.minCapacity = p.minCapacity();
        this.estimatedFlightTime = p.estimatedFlightTime();
        this.status = p.status();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }
}
