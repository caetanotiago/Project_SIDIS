package isep.psoft.aisafe.airports.query;

import isep.psoft.aisafe.airports.events.AirportEventPayload;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "REPLICATED_AIRPORT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedAirport {
    @Id
    @Column(length = 3)
    private String iataCode;

    private String name;
    private String city;
    private String country;
    private String region;
    private String timezone;
    private Double latitude;
    private Double longitude;
    private String status;
    private Integer terminals;
    private Integer gates;
    private String operationalHours;
    private String contactEmail;
    private String contactPhone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_AIRPORT_RUNWAY", joinColumns = @JoinColumn(name = "IATA_CODE"))
    @OrderColumn(name = "POSITION")
    private List<ReplicatedRunway> runways = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_AIRPORT_CERTIFICATION", joinColumns = @JoinColumn(name = "IATA_CODE"))
    @Column(name = "AIRCRAFT_MODEL")
    private Set<String> certifiedModels = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_AIRPORT_SERVICE", joinColumns = @JoinColumn(name = "IATA_CODE"))
    @Column(name = "SERVICE")
    private Set<String> services = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_AIRPORT_PHOTO", joinColumns = @JoinColumn(name = "IATA_CODE"))
    @OrderColumn(name = "POSITION")
    @Column(name = "URL")
    private List<String> photoUrls = new ArrayList<>();

    private Long sourceVersion;

    private String ownerInstance;

    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedAirport(String iataCode) {
        this.iataCode = iataCode;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(AirportEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        this.name = p.name();
        this.city = p.city();
        this.country = p.country();
        this.region = p.region();
        this.timezone = p.timezone();
        this.latitude = p.latitude();
        this.longitude = p.longitude();
        this.status = p.status();
        this.terminals = p.terminals();
        this.gates = p.gates();
        this.operationalHours = p.operationalHours();
        this.contactEmail = p.contactEmail();
        this.contactPhone = p.contactPhone();
        this.runways.clear();
        p.runways().forEach(r -> this.runways.add(
                new ReplicatedRunway(r.name(), r.lengthMeters(), r.orientation(), r.surface())));
        this.certifiedModels.clear();
        this.certifiedModels.addAll(p.certifiedModels());
        this.services.clear();
        this.services.addAll(p.services());
        this.photoUrls.clear();
        this.photoUrls.addAll(p.photoUrls());
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }
}
