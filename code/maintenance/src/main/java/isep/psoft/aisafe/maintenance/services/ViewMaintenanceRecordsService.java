package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ViewMaintenanceRecordsService {

    private final MaintenanceReplicaQueries replicas;
    private final AircraftClient aircraftClient;

    public MaintenanceRecordDTO getById(String id) {
        return replicas.findRecord(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
    }

    public PageDTO<MaintenanceRecordDTO> byAircraft(String registration, int page, int size) {
        String reg = registration.trim().toUpperCase();
        if (!replicas.isPeerRequest()) {
            aircraftClient.getAircraft(reg);
        }
        return PageDTO.of(replicas.searchRecords(RecordFilter.byAircraft(reg)), page, size);
    }

    public PageDTO<MaintenanceRecordDTO> search(RecordFilter filter, int page, int size) {
        return PageDTO.of(replicas.searchRecords(filter), page, size);
    }

    public PageDTO<MaintenanceRecordDTO> ongoing(int page, int size) {
        return PageDTO.of(replicas.searchRecords(RecordFilter.ongoing()), page, size);
    }

    public List<MaintenanceRecordDTO> searchAll(RecordFilter filter) {
        return replicas.searchRecords(filter);
    }
}
