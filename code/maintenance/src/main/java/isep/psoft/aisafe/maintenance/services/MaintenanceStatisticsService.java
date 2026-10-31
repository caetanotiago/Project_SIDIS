package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.domain.CostGrouping;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO.CostItem;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO.AircraftHours;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO.TurnaroundItem;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * US117, US220, US221 e o resumo da US222.
 *
 * <p>Cada registo vive numa só réplica, por isso cada réplica calcula SOMAS e CONTAGENS sobre os
 * seus registos e a réplica que recebeu o pedido soma os resultados parciais dos peers (que chama
 * no MESMO endpoint). Médias e horas só são calculadas depois da soma — a média das médias de cada
 * réplica estaria errada.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceStatisticsService {

    static final String HOURS_URI = "/api/maintenance-records/statistics/hours";
    static final String COSTS_URI = "/api/maintenance-records/reports/costs";
    static final String TURNAROUND_URI = "/api/maintenance-records/reports/turnaround";
    static final String LAST_COMPLETIONS_URI = "/api/maintenance-records/statistics/last-completions";

    private static final ParameterizedTypeReference<FleetMaintenanceHoursDTO> HOURS_TYPE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<CostReportDTO> COSTS_TYPE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<TurnaroundReportDTO> TURNAROUND_TYPE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<LastCompletionDTO>> LAST_TYPE = new ParameterizedTypeReference<>() { };

    private final MaintenanceRecordRepository repository;
    private final PeerClient peerClient;

    // ------------------------------------------------------------------ US117

    public FleetMaintenanceHoursDTO maintenanceHours() {
        Map<String, AircraftHours> byAircraft = new TreeMap<>();
        for (MaintenanceRecord r : repository.findAll()) {
            add(byAircraft, new AircraftHours(r.getAircraftRegistration(), r.getAircraftModel(), 1, r.maintenanceMinutes(), 0));
        }
        peerClient.collect(HOURS_URI, HOURS_TYPE).responses()
                .forEach(partial -> partial.aircraft().forEach(a -> add(byAircraft, a)));

        List<AircraftHours> rows = byAircraft.values().stream()
                .map(a -> new AircraftHours(a.aircraftRegistration(), a.aircraftModel(), a.records(), a.minutes(),
                        FleetMaintenanceHoursDTO.hours(a.minutes())))
                .toList();
        long total = rows.stream().mapToLong(AircraftHours::minutes).sum();
        return new FleetMaintenanceHoursDTO(total, FleetMaintenanceHoursDTO.hours(total), rows);
    }

    private static void add(Map<String, AircraftHours> acc, AircraftHours a) {
        acc.merge(a.aircraftRegistration(), a, (x, y) ->
                new AircraftHours(x.aircraftRegistration(), x.aircraftModel(), x.records() + y.records(),
                        x.minutes() + y.minutes(), 0));
    }

    // ------------------------------------------------------------------ US220

    public CostReportDTO costs(CostGrouping grouping) {
        Map<String, CostItem> items = new TreeMap<>();
        for (MaintenanceRecord r : repository.findByStatus(MaintenanceStatus.COMPLETED)) {
            add(items, new CostItem(grouping.keyOf(r), r.getCompletion().getCost(), 1));
        }
        String uri = MaintenanceReplicaQueries.uri(COSTS_URI, "groupBy", grouping);
        peerClient.collect(uri, COSTS_TYPE).responses()
                .forEach(partial -> partial.items().forEach(i -> add(items, i)));

        List<CostItem> rows = items.values().stream()
                .sorted(Comparator.comparing(CostItem::totalCost).reversed().thenComparing(CostItem::key))
                .toList();
        BigDecimal total = rows.stream().map(CostItem::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        long records = rows.stream().mapToLong(CostItem::records).sum();
        return new CostReportDTO(grouping.name(), total, records, rows);
    }

    private static void add(Map<String, CostItem> acc, CostItem i) {
        acc.merge(i.key(), i, (x, y) -> new CostItem(x.key(), x.totalCost().add(y.totalCost()), x.records() + y.records()));
    }

    // ------------------------------------------------------------------ US221

    public TurnaroundReportDTO turnaround() {
        Map<String, long[]> byModel = new TreeMap<>(); // modelo → [registos concluídos, minutos]
        for (MaintenanceRecord r : repository.findByStatus(MaintenanceStatus.COMPLETED)) {
            add(byModel, r.getAircraftModel(), 1, r.getCompletion().getActualDurationMinutes());
        }
        peerClient.collect(TURNAROUND_URI, TURNAROUND_TYPE).responses()
                .forEach(partial -> partial.items().forEach(i ->
                        add(byModel, i.aircraftModel(), i.completedRecords(), i.totalMinutes())));

        List<TurnaroundItem> rows = new ArrayList<>();
        byModel.forEach((model, sums) -> rows.add(TurnaroundItem.of(model, sums[0], sums[1])));
        return new TurnaroundReportDTO(rows);
    }

    private static void add(Map<String, long[]> acc, String model, long count, long minutes) {
        long[] sums = acc.computeIfAbsent(model, m -> new long[2]);
        sums[0] += count;
        sums[1] += minutes;
    }

    // ------------------------------------------------------------------ US222

    /** Por (aeronave, template): última conclusão em todas as réplicas e se há um registo em curso. */
    public List<LastCompletionDTO> lastCompletions() {
        Map<String, LastCompletionDTO> merged = new TreeMap<>();
        for (MaintenanceRecord r : repository.findAll()) {
            boolean completed = !r.isOngoing();
            add(merged, new LastCompletionDTO(r.getAircraftRegistration(), r.getTemplate().getTemplateId(),
                    completed ? r.getCompletion().getCompletionDate() : null,
                    completed ? r.getCompletion().getAircraftFlightHours() : null,
                    r.isOngoing()));
        }
        peerClient.collect(LAST_COMPLETIONS_URI, LAST_TYPE).responses()
                .forEach(list -> list.forEach(l -> add(merged, l)));
        return List.copyOf(merged.values());
    }

    private static void add(Map<String, LastCompletionDTO> acc, LastCompletionDTO l) {
        acc.merge(l.key(), l, LastCompletionDTO::merge);
    }
}
