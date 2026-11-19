package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One entry of a route's history in the read model (copied from {@link RouteEventPayload.HistoryEntry}). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedRouteHistory {

    private LocalDateTime changeDate;
    private String description;
    private Double previousMinRange;
    private Integer previousMinCapacity;
    private Integer previousEstimatedFlightTime;
    private String previousStatus;
    private Double newMinRange;
    private Integer newMinCapacity;
    private Integer newEstimatedFlightTime;
    private String newStatus;

    static ReplicatedRouteHistory of(RouteEventPayload.HistoryEntry e) {
        ReplicatedRouteHistory h = new ReplicatedRouteHistory();
        h.changeDate = e.changeDate();
        h.description = e.description();
        h.previousMinRange = e.previousMinRange();
        h.previousMinCapacity = e.previousMinCapacity();
        h.previousEstimatedFlightTime = e.previousEstimatedFlightTime();
        h.previousStatus = e.previousStatus();
        h.newMinRange = e.newMinRange();
        h.newMinCapacity = e.newMinCapacity();
        h.newEstimatedFlightTime = e.newEstimatedFlightTime();
        h.newStatus = e.newStatus();
        return h;
    }
}
