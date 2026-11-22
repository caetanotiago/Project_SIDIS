package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.domain.CostGrouping;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO.CostItem;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO.AircraftHours;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO.TurnaroundItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class MaintenanceStatisticsService {

    private final ReplicatedMaintenanceRecordRepository readModel;

    @Transactional(readOnly = true)
    public FleetMaintenanceHoursDTO maintenanceHours() {
        Map<String, long[]> byAircraft = new TreeMap<>();
        Map<String, String> models = new TreeMap<>();
        for (ReplicatedMaintenanceRecord r : readModel.findAll()) {
            long[] sums = byAircraft.computeIfAbsent(r.getAircraftRegistration(), k -> new long[2]);
            sums[0]++;
            sums[1] += r.maintenanceMinutes();
            models.putIfAbsent(r.getAircraftRegistration(), r.getAircraftModel());
        }
        List<AircraftHours> rows = new ArrayList<>();
        byAircraft.forEach((reg, sums) -> rows.add(new AircraftHours(reg, models.get(reg), sums[0], sums[1],
                FleetMaintenanceHoursDTO.hours(sums[1]))));
        long total = rows.stream().mapToLong(AircraftHours::minutes).sum();
        return new FleetMaintenanceHoursDTO(total, FleetMaintenanceHoursDTO.hours(total), rows);
    }

    @Transactional(readOnly = true)
    public CostReportDTO costs(CostGrouping grouping) {
        Map<String, CostItem> items = new TreeMap<>();
        for (ReplicatedMaintenanceRecord r : readModel.findByStatus(MaintenanceStatus.COMPLETED.name())) {
            String key = grouping.keyOf(r.getAircraftRegistration(), r.getAircraftModel());
            BigDecimal cost = r.getCost() == null ? BigDecimal.ZERO : r.getCost();
            items.merge(key, new CostItem(key, cost, 1),
                    (x, y) -> new CostItem(x.key(), x.totalCost().add(y.totalCost()), x.records() + y.records()));
        }
        List<CostItem> rows = items.values().stream()
                .sorted(Comparator.comparing(CostItem::totalCost).reversed().thenComparing(CostItem::key))
                .toList();
        BigDecimal total = rows.stream().map(CostItem::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        long records = rows.stream().mapToLong(CostItem::records).sum();
        return new CostReportDTO(grouping.name(), total, records, rows);
    }

    @Transactional(readOnly = true)
    public TurnaroundReportDTO turnaround() {
        Map<String, long[]> byModel = new TreeMap<>();
        for (ReplicatedMaintenanceRecord r : readModel.findByStatus(MaintenanceStatus.COMPLETED.name())) {
            long[] sums = byModel.computeIfAbsent(r.getAircraftModel(), m -> new long[2]);
            sums[0]++;
            sums[1] += r.getActualDurationMinutes() == null ? 0 : r.getActualDurationMinutes();
        }
        List<TurnaroundItem> rows = new ArrayList<>();
        byModel.forEach((model, sums) -> rows.add(TurnaroundItem.of(model, sums[0], sums[1])));
        return new TurnaroundReportDTO(rows);
    }

    @Transactional(readOnly = true)
    public Map<String, LastCompletionDTO> lastCompletions() {
        Map<String, LastCompletionDTO> merged = new TreeMap<>();
        for (ReplicatedMaintenanceRecord r : readModel.findAll()) {
            boolean completed = r.isCompleted();
            LastCompletionDTO entry = new LastCompletionDTO(r.getAircraftRegistration(), r.getTemplateId(),
                    completed ? r.getCompletionDate() : null,
                    completed ? r.getAircraftFlightHours() : null,
                    r.isOngoing());
            merged.merge(entry.key(), entry, LastCompletionDTO::merge);
        }
        return merged;
    }
}
