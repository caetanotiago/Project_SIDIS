package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import isep.psoft.aisafe.maintenance.query.external.FleetDirectory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ViewMaintenanceRecordsService {

    static final Comparator<ReplicatedMaintenanceRecord> MOST_RECENT_FIRST =
            Comparator.comparing(ReplicatedMaintenanceRecord::getStartDate,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ReplicatedMaintenanceRecord::getRecordId);

    private final ReplicatedMaintenanceRecordRepository readModel;
    private final MaintenanceRecordAssembler assembler;
    private final FleetDirectory fleet;

    @Transactional(readOnly = true)
    public MaintenanceRecordDTO getById(String id) {
        return readModel.findById(id)
                .map(assembler::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
    }

    @Transactional
    public PageDTO<MaintenanceRecordDTO> byAircraft(String registration, int page, int size) {
        String reg = registration.trim().toUpperCase();
        fleet.aircraft(reg);
        return PageDTO.of(search(RecordFilter.byAircraft(reg)), page, size);
    }

    @Transactional(readOnly = true)
    public PageDTO<MaintenanceRecordDTO> search(RecordFilter filter, int page, int size) {
        return PageDTO.of(search(filter), page, size);
    }

    @Transactional(readOnly = true)
    public PageDTO<MaintenanceRecordDTO> ongoing(int page, int size) {
        return PageDTO.of(search(RecordFilter.ongoing()), page, size);
    }

    private List<MaintenanceRecordDTO> search(RecordFilter filter) {
        return readModel.findAll().stream()
                .filter(filter::matches)
                .sorted(MOST_RECENT_FIRST)
                .map(assembler::toDTO)
                .toList();
    }
}
