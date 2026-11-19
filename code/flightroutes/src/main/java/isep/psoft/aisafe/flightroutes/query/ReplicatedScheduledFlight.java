package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEventPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * READ MODEL of a scheduled flight (table {@code REPLICATED_SCHEDULED_FLIGHT}), built ONLY from scheduled-flight
 * events, exactly like {@link ReplicatedRoute}: every replica holds the flights of all replicas, so the flight
 * queries and statistics (US206, US213, US214) are answered locally.
 */
@Entity
@Table(name = "REPLICATED_SCHEDULED_FLIGHT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedScheduledFlight {

    @Id
    private String id;

    private String aircraftRegistration;
    private String routeId;

    @Column(length = 3)
    private String originIata;

    @Column(length = 3)
    private String destinationIata;

    /** Minutes, copied from the route when the flight was scheduled. */
    private Integer estimatedFlightTime;

    @Column(name = "flight_date")
    private LocalDate date;

    @Column(name = "flight_time")
    private LocalTime time;

    private String status;

    private Long sourceVersion;
    private String ownerInstance;
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedScheduledFlight(String id) {
        this.id = id;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(ScheduledFlightEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        this.aircraftRegistration = p.aircraftRegistration();
        this.routeId = p.routeId();
        this.originIata = p.originIata();
        this.destinationIata = p.destinationIata();
        this.estimatedFlightTime = p.estimatedFlightTime();
        this.date = p.date();
        this.time = p.time();
        this.status = p.status();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }

    /** {@code ScheduledFlightStatusChanged}: only the status (and the event metadata) change. */
    void applyStatus(String status, Long version, String eventId, Instant eventAt) {
        this.status = status;
        this.sourceVersion = version;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }
}
