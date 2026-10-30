package isep.psoft.aisafe.airports.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agregado Airport (WP#2). A identidade é o código IATA — o mesmo identificador com que os
 * outros microsserviços o referenciam — e não um id técnico.
 *
 * <p>Distribuição: cada aeroporto é guardado na réplica que recebeu o registo (réplica dona).
 * As alterações são sempre executadas nessa réplica; {@link Version} deteta escritas concorrentes
 * dentro dela (409).
 */
@Entity
@Table(name = "airport")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airport {

    @Id
    @Column(name = "iata_code", length = 3)
    private String iataCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    /** Região geográfica (ex. "Europe") — usada no agrupamento da US211. Opcional. */
    private String region;

    /** Fuso horário IANA, ex. "Europe/Lisbon". */
    @Column(nullable = false)
    private String timezone;

    @Embedded
    private Coordinates coordinates;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AirportStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "airport_runway", joinColumns = @JoinColumn(name = "iata_code"))
    @OrderColumn(name = "position")
    private List<Runway> runways = new ArrayList<>();

    // ---- US106a: modelos de aeronave certificados para operar neste aeroporto
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "airport_certification", joinColumns = @JoinColumn(name = "iata_code"))
    @Column(name = "aircraft_model")
    private Set<String> certifiedModels = new LinkedHashSet<>();

    // ---- US207: instalações (opcionais)
    private Integer terminals;

    private Integer gates;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "airport_service", joinColumns = @JoinColumn(name = "iata_code"))
    @Column(name = "service")
    private Set<String> services = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "airport_photo", joinColumns = @JoinColumn(name = "iata_code"))
    @OrderColumn(name = "position")
    @Column(name = "url")
    private List<String> photoUrls = new ArrayList<>();

    // ---- US208: horário e contactos (opcionais)
    private String operationalHours;

    private String contactEmail;

    private String contactPhone;

    @Version
    private Long version;

    public Airport(String iataCode, String name, String city, String country, String region,
                   String timezone, Coordinates coordinates, List<Runway> runways) {
        this.iataCode = IataCode.normalize(iataCode);
        this.name = required(name, "Airport name");
        this.city = required(city, "City");
        this.country = required(country, "Country");
        this.region = optional(region);
        this.timezone = validTimezone(timezone);
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates are required.");
        }
        this.coordinates = coordinates;
        if (runways == null || runways.isEmpty()) {
            throw new IllegalArgumentException("At least one runway is required.");
        }
        Set<String> names = new HashSet<>();
        for (Runway r : runways) {
            if (!names.add(r.getName())) {
                throw new IllegalArgumentException("Duplicate runway name: " + r.getName());
            }
        }
        this.runways.addAll(runways);
        this.status = AirportStatus.OPERATIONAL; // um aeroporto registado nasce operacional
    }

    /** US207 — instalações opcionais (terminais, portas de embarque, serviços, fotos). */
    public void describeFacilities(Integer terminals, Integer gates, Set<String> services, List<String> photoUrls) {
        if (terminals != null && terminals < 0) {
            throw new IllegalArgumentException("Number of terminals cannot be negative.");
        }
        if (gates != null && gates < 0) {
            throw new IllegalArgumentException("Number of gates cannot be negative.");
        }
        this.terminals = terminals;
        this.gates = gates;
        this.services.clear();
        if (services != null) {
            services.stream().map(Airport::optional).filter(s -> s != null).forEach(this.services::add);
        }
        this.photoUrls.clear();
        if (photoUrls != null) {
            photoUrls.stream().map(Airport::optional).filter(s -> s != null).forEach(this.photoUrls::add);
        }
    }

    /** US208 — horário de funcionamento e contactos (null = não altera). */
    public void updateContactDetails(String operationalHours, String contactEmail, String contactPhone) {
        if (operationalHours != null) {
            this.operationalHours = optional(operationalHours);
        }
        if (contactEmail != null) {
            this.contactEmail = optional(contactEmail);
        }
        if (contactPhone != null) {
            this.contactPhone = optional(contactPhone);
        }
    }

    /**
     * US208 — atualização parcial dos detalhes: horário, contactos e instalações.
     * Cada parâmetro a null = atributo não alterado; uma lista/conjunto vazio limpa o atributo.
     */
    public void updateDetails(String operationalHours, String contactEmail, String contactPhone,
                              Integer terminals, Integer gates, Set<String> services, List<String> photoUrls) {
        updateContactDetails(operationalHours, contactEmail, contactPhone);
        describeFacilities(
                terminals != null ? terminals : this.terminals,
                gates != null ? gates : this.gates,
                services != null ? services : new LinkedHashSet<>(this.services),
                photoUrls != null ? photoUrls : new ArrayList<>(this.photoUrls));
    }

    /** US109 — mudar o estado operacional (operational, closed, under maintenance). */
    public void changeStatus(AirportStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Airport status is required.");
        }
        this.status = newStatus;
    }

    /**
     * US106a — certificar um modelo de aeronave para operar neste aeroporto.
     * O modelo é referenciado pelo nome (identidade no Aircraft Management), tal como o IATA é
     * referenciado pelos outros serviços. Certificar o mesmo modelo duas vezes → 409.
     */
    public void certifyModel(String aircraftModel) {
        String model = required(aircraftModel, "Aircraft model");
        boolean alreadyCertified = certifiedModels.stream().anyMatch(m -> m.equalsIgnoreCase(model));
        if (alreadyCertified) {
            throw new IllegalStateException("Aircraft model " + model + " is already certified for airport " + iataCode + ".");
        }
        certifiedModels.add(model);
    }

    public boolean isCertifiedFor(String aircraftModel) {
        return aircraftModel != null
                && certifiedModels.stream().anyMatch(m -> m.equalsIgnoreCase(aircraftModel.trim()));
    }

    public boolean isOperational() {
        return status == AirportStatus.OPERATIONAL;
    }

    public List<Runway> getRunways() {
        return Collections.unmodifiableList(runways);
    }

    public Set<String> getCertifiedModels() {
        return Collections.unmodifiableSet(certifiedModels);
    }

    public Set<String> getServices() {
        return Collections.unmodifiableSet(services);
    }

    public List<String> getPhotoUrls() {
        return Collections.unmodifiableList(photoUrls);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String validTimezone(String timezone) {
        String tz = required(timezone, "Timezone");
        try {
            return ZoneId.of(tz).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid timezone: " + tz + " (expected an IANA id, e.g. Europe/Lisbon).");
        }
    }
}
