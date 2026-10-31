package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pure Fabrication: protocolo entre a Maintenance e o estado da aeronave no Aircraft Management
 * (B3b). O estado da aeronave é a única fonte de verdade da disponibilidade — o Flight Routes só
 * agenda voos para aeronaves AVAILABLE, por isso "aircraft under maintenance cannot be assigned to
 * flights" é garantido aqui.
 */
@Service
@RequiredArgsConstructor
public class AircraftAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AircraftAvailabilityService.class);

    private final AircraftClient aircraftClient;
    private final MaintenanceReplicaQueries replicas;

    /**
     * US115: aeronave → UNDER_MAINTENANCE. Chamado DEPOIS do saveAndFlush do registo e ANTES do
     * commit: se falhar, a exceção faz rollback do registo; se o commit falhar depois disto, é
     * enviado um PATCH de compensação com o estado anterior.
     */
    public void markUnderMaintenance(AircraftSnapshot aircraft) {
        String previous = aircraft.status();
        aircraftClient.updateStatus(aircraft.registrationNumber(), AircraftSnapshot.UNDER_MAINTENANCE);
        if (previous != null && !AircraftSnapshot.UNDER_MAINTENANCE.equalsIgnoreCase(previous)) {
            compensateOnRollback(aircraft.registrationNumber(), previous);
        }
    }

    /**
     * US119: aeronave → AVAILABLE, só se não houver outro registo dela em curso em NENHUMA réplica e
     * todas as réplicas tiverem respondido. Caso contrário (ou se o Aircraft Management falhar) a
     * aeronave fica UNDER_MAINTENANCE — o lado seguro — e o ATCC pode alterá-la com a US105.
     *
     * @return true se a aeronave foi libertada
     */
    public boolean releaseIfNoOtherOngoing(String registration, String completedRecordId) {
        MaintenanceReplicaQueries.RecordSearch ongoing = replicas.searchRecordsWithStatus(RecordFilter.ongoingOf(registration));
        boolean otherOngoing = ongoing.records().stream().anyMatch(r -> !r.getId().equals(completedRecordId));
        if (otherOngoing) {
            log.info("Aircraft {} keeps UNDER_MAINTENANCE: other maintenance records are still in progress", registration);
            return false;
        }
        if (!ongoing.complete()) {
            log.warn("Aircraft {} keeps UNDER_MAINTENANCE: not every replica answered, other records may be in progress",
                    registration);
            return false;
        }
        try {
            aircraftClient.updateStatus(registration, AircraftSnapshot.AVAILABLE);
            log.info("Aircraft {} released to AVAILABLE after its last maintenance record was completed", registration);
            return true;
        } catch (RuntimeException e) {
            log.warn("Aircraft {} keeps UNDER_MAINTENANCE: Aircraft Management did not accept the release ({})",
                    registration, e.getMessage());
            return false;
        }
    }

    private void compensateOnRollback(String registration, String previousStatus) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    aircraftClient.updateStatus(registration, previousStatus);
                    log.warn("Compensation: aircraft {} restored to {} after the local transaction rolled back",
                            registration, previousStatus);
                } catch (RuntimeException e) {
                    log.error("Compensation failed: aircraft {} could not be restored to {}", registration, previousStatus, e);
                }
            }
        });
    }
}
