package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** US116, US218, US219 e consulta por id — sempre com os dados de todas as réplicas. */
@Service
@RequiredArgsConstructor
public class ViewMaintenanceRecordsService {

    private final MaintenanceReplicaQueries replicas;
    private final AircraftClient aircraftClient;

    public MaintenanceRecordDTO getById(String id) {
        return replicas.findRecord(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
    }

    /** US116: a aeronave tem de existir (404, não um histórico vazio). */
    public PageDTO<MaintenanceRecordDTO> byAircraft(String registration, int page, int size) {
        String reg = registration.trim().toUpperCase();
        if (!replicas.isPeerRequest()) {
            aircraftClient.getAircraft(reg); // B3a: 404 se não existir
        }
        return PageDTO.of(replicas.searchRecords(RecordFilter.byAircraft(reg)), page, size);
    }

    /** US218: pesquisa por aeronave, componente, estado e intervalo de datas (de início). */
    public PageDTO<MaintenanceRecordDTO> search(RecordFilter filter, int page, int size) {
        return PageDTO.of(replicas.searchRecords(filter), page, size);
    }

    /** US219: manutenções em curso em toda a frota. */
    public PageDTO<MaintenanceRecordDTO> ongoing(int page, int size) {
        return PageDTO.of(replicas.searchRecords(RecordFilter.ongoing()), page, size);
    }

    /** Pesquisa não paginada (endpoint /search, usado também pelas réplicas peer). */
    public List<MaintenanceRecordDTO> searchAll(RecordFilter filter) {
        return replicas.searchRecords(filter);
    }
}
