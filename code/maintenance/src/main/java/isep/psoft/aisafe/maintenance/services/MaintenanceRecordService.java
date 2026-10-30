package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.domain.Maintenance;
import isep.psoft.aisafe.maintenance.domain.MaintenanceReport;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRequest;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.ScheduleMaintenanceRequest;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for maintenance records and replica collaboration.
 *
 * @author Diogo Fernandes - 1240632
 */
@Service
@RequiredArgsConstructor
public class MaintenanceRecordService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRecordService.class);

    private static final ParameterizedTypeReference<List<MaintenanceRecordDTO>> RECORD_LIST =
            new ParameterizedTypeReference<>() { };

    private static final Comparator<MaintenanceRecordDTO> MOST_RECENT_FIRST =
            Comparator.comparing(MaintenanceRecordDTO::scheduledDate).reversed()
                    .thenComparing(MaintenanceRecordDTO::id, Comparator.reverseOrder());

    private final AircraftClient aircraftClient;
    private final MaintenanceRepository maintenanceRepository;
    private final PeerClient peerClient;

    @Transactional
    public MaintenanceRecordDTO start(ScheduleMaintenanceRequest request) {
        Maintenance maintenance = new Maintenance(
                request.aircraftRegistration(),
                request.scheduledDate(),
                request.serviceType());

        aircraftClient.getAircraft(maintenance.getAircraftRegistration());
        aircraftClient.updateStatus(maintenance.getAircraftRegistration(), "UNDER_MAINTENANCE");

        Maintenance saved;
        try {
            saved = maintenanceRepository.saveAndFlush(maintenance);
        } catch (RuntimeException saveFailure) {
            try {
                aircraftClient.updateStatus(maintenance.getAircraftRegistration(), "AVAILABLE");
            } catch (RuntimeException compensationFailure) {
                if (compensationFailure != saveFailure) {
                    saveFailure.addSuppressed(compensationFailure);
                }
                log.error("Failed to restore aircraft {} status after maintenance record save failure.",
                        maintenance.getAircraftRegistration(), compensationFailure);
            }
            throw saveFailure;
        }
        return MaintenanceRecordDTO.from(saved);
    }

    @Transactional
    public MaintenanceRecordDTO complete(String id, CompleteMaintenanceRequest request) {
        Optional<Maintenance> localRecord = maintenanceRepository.findById(id);
        if (localRecord.isEmpty()) {
            return peerClient.forwardToOwner(
                            HttpMethod.PATCH,
                            "/api/maintenance/" + pathSegment(id) + "/complete",
                            request,
                            MaintenanceRecordDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
        }

        Maintenance maintenance = localRecord.get();
        MaintenanceReport report = new MaintenanceReport(request.report().text());
        maintenance.validateCompletion(report, request.completionDate());
        aircraftClient.updateStatus(maintenance.getAircraftRegistration(), "AVAILABLE");
        maintenance.complete(report, request.completionDate());
        return MaintenanceRecordDTO.from(maintenanceRepository.saveAndFlush(maintenance));
    }

    @Transactional(readOnly = true)
    public MaintenanceRecordDTO getById(String id) {
        return maintenanceRepository.findById(id)
                .map(MaintenanceRecordDTO::from)
                .or(() -> peerClient.findFirst(
                        "/api/maintenance/" + pathSegment(id),
                        MaintenanceRecordDTO.class))
                .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecordDTO> listAll() {
        List<MaintenanceRecordDTO> localRecords = maintenanceRepository.findAllByOrderByScheduledDateDescIdDesc()
                .stream()
                .map(MaintenanceRecordDTO::from)
                .toList();
        return mergeWithPeers(localRecords, "/api/maintenance");
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecordDTO> history(String registration) {
        if (registration == null || registration.isBlank()) {
            throw new IllegalArgumentException("Aircraft registration is required.");
        }

        String normalizedRegistration = registration.trim();
        aircraftClient.getAircraft(normalizedRegistration);
        List<MaintenanceRecordDTO> localRecords = maintenanceRepository
                .findByAircraftRegistrationOrderByScheduledDateDescIdDesc(normalizedRegistration)
                .stream()
                .map(MaintenanceRecordDTO::from)
                .toList();
        String uri = "/api/maintenance/aircraft/" + pathSegment(normalizedRegistration);
        return mergeWithPeers(localRecords, uri);
    }

    private List<MaintenanceRecordDTO> mergeWithPeers(List<MaintenanceRecordDTO> localRecords, String uri) {
        Map<String, MaintenanceRecordDTO> recordsById = new LinkedHashMap<>();
        localRecords.forEach(record -> recordsById.put(record.id(), record));
        peerClient.collectList(uri, RECORD_LIST)
                .forEach(record -> recordsById.putIfAbsent(record.id(), record));
        return recordsById.values().stream().sorted(MOST_RECENT_FIRST).toList();
    }

    private static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
