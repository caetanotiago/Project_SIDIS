package isep.psoft.aisafe.aircraftmanagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.LocalDate;

/** Aeronave concreta (US102). A matrícula é o identificador e é guardada tal como recebida. */
@Entity
public class Aircraft {

    @Id
    private String registrationNumber;

    private String modelName;

    private int seatingCapacity;

    private LocalDate manufacturingDate;

    @Enumerated(EnumType.STRING)
    private AircraftStatus status;

    /** Optimistic locking: dois PATCH concorrentes → 409 (ver GlobalExceptionHandler). */
    @Version
    private Long version;

    protected Aircraft() {
        // JPA
    }

    public Aircraft(String registrationNumber, String modelName, int seatingCapacity,
                    LocalDate manufacturingDate, AircraftStatus status) {
        if (registrationNumber == null || registrationNumber.isBlank()) {
            throw new IllegalArgumentException("Registration number cannot be empty");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be empty");
        }
        if (seatingCapacity <= 0) {
            throw new IllegalArgumentException("Seating capacity must be positive");
        }
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.seatingCapacity = seatingCapacity;
        this.manufacturingDate = manufacturingDate;
        this.status = status == null ? AircraftStatus.AVAILABLE : status;
    }

    /**
     * US105. Regra proposta para a decisão Q7 do contrato: uma aeronave em voo não pode passar
     * a UNDER_MAINTENANCE nem INACTIVE (→ IllegalStateException → 409).
     */
    public void changeStatus(AircraftStatus target) {
        if (target == this.status) {
            return;
        }
        if (this.status == AircraftStatus.IN_FLIGHT
                && (target == AircraftStatus.UNDER_MAINTENANCE || target == AircraftStatus.INACTIVE)) {
            throw new IllegalStateException(
                    "Aircraft " + registrationNumber + " is in flight and cannot change to " + target);
        }
        this.status = target;
    }

    public AircraftSummary toSummary() {
        return new AircraftSummary(registrationNumber, modelName, seatingCapacity, manufacturingDate, status);
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public String getModelName() { return modelName; }
    public int getSeatingCapacity() { return seatingCapacity; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public AircraftStatus getStatus() { return status; }
}
