package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** US103 (ponte B1a) e US104 (pesquisa agregada entre réplicas). */
@Service
@RequiredArgsConstructor
public class SearchAircraftsService {

    private final AircraftReplicaQueries replicas;
    private final AircraftRepository repository;

    public AircraftSummary getByRegistration(String registrationNumber) {
        return replicas.findAircraft(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
    }

    /**
     * US104: pesquisa por modelo, estado e/ou ano de fabrico. Filtros opcionais (combinam-se com AND).
     * Devolve a lista completa ordenada por matrícula: dados locais + dados dos peers.
     * Num pedido de um peer devolve só os dados locais.
     */
    public List<AircraftSummary> search(String modelName, String status, Integer manufacturingYear) {
        AircraftStatus statusFilter = (status == null || status.isBlank()) ? null : AircraftStatus.parse(status);

        List<AircraftSummary> local = repository.findAll().stream()
                .filter(a -> modelName == null || modelName.isBlank() || a.getModelName().equals(modelName))
                .filter(a -> statusFilter == null || a.getStatus() == statusFilter)
                .filter(a -> manufacturingYear == null || matchesYear(a, manufacturingYear))
                .map(Aircraft::toSummary)
                .toList();

        List<AircraftSummary> result = replicas.isPeerRequest()
                ? local
                : replicas.withPeers(local, AircraftReplicaQueries.uri("/api/aircrafts",
                        "modelName", modelName, "status", statusFilter, "manufacturingYear", manufacturingYear));

        return result.stream().sorted(Comparator.comparing(AircraftSummary::registrationNumber)).toList();
    }

    public boolean isPeerRequest() {
        return replicas.isPeerRequest();
    }

    private static boolean matchesYear(Aircraft a, int year) {
        return a.getManufacturingDate() != null && a.getManufacturingDate().getYear() == year;
    }
}
