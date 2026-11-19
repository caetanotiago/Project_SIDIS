package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * READ MODEL of a route (table {@code REPLICATED_ROUTE} in this replica's own database), built ONLY from route
 * events. Every replica holds every route here, whichever replica owns it, so every GET of routes is answered
 * locally (CQRS query side). The write model ({@code FlightRoute}) stays partitioned: each route is stored and
 * updated only by its owner replica.
 *
 * <p>On the owner replica the row is written in the same transaction as the write model (read-your-writes); on the
 * other replicas it is written when the event arrives (eventual consistency).
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

    /** US111: the route history, replaced as a whole by every event (oldest first). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_ROUTE_HISTORY", joinColumns = @JoinColumn(name = "ROUTE_ID"))
    @OrderColumn(name = "POSITION")
    private List<ReplicatedRouteHistory> history = new ArrayList<>();

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
        this.history.clear();
        p.history().forEach(h -> this.history.add(ReplicatedRouteHistory.of(h)));
    }

    public RouteSummary toSummary() {
        return new RouteSummary(id, originIata, destinationIata, distance, minRange, minCapacity,
                estimatedFlightTime, status);
    }
}
