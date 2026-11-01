package isep.psoft.aisafe.distributed;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries;
import isep.sidis.common.peers.PeerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Consultas entre réplicas (Integration-Contracts §5): local primeiro, depois peers;
 * coleções = locais + peers sem duplicados.
 */
@ExtendWith(MockitoExtension.class)
class ReplicaQueriesDistributedTest {

    private static final LocalDate DATE = LocalDate.of(2020, 1, 1);

    @Mock private AircraftRepository aircraftRepository;
    @Mock private AircraftModelRepository modelRepository;
    @Mock private PeerClient peerClient;

    private AircraftReplicaQueries replicas;

    @BeforeEach
    void setUp() {
        replicas = new AircraftReplicaQueries(aircraftRepository, modelRepository, peerClient, new AircraftAssembler());
    }

    private static AircraftDTO dto(String reg, String model, String status) {
        AircraftDTO d = new AircraftDTO();
        d.setRegistrationNumber(reg);
        d.setModelName(model);
        d.setSeatingCapacity(180);
        d.setManufacturingDate(DATE);
        d.setStatus(status);
        return d;
    }

    private static AircraftSummary summary(String reg, AircraftStatus status) {
        return new AircraftSummary(reg, "A320", 180, DATE, status);
    }

    // ------------------------------------------------------------------ findAircraft

    @Test
    void findAircraftPrefersLocalReplicaAndSkipsPeers() {
        when(aircraftRepository.findById("CS-TUA"))
                .thenReturn(Optional.of(new Aircraft("CS-TUA", "A320", 180, DATE, AircraftStatus.AVAILABLE)));

        Optional<AircraftSummary> result = replicas.findAircraft("CS-TUA");

        assertEquals(Optional.of(summary("CS-TUA", AircraftStatus.AVAILABLE)), result);
        verifyNoInteractions(peerClient);
    }

    @Test
    void findAircraftFallsBackToPeerWhenNotLocal() {
        when(aircraftRepository.findById("CS-TUA")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircrafts/CS-TUA"), eq(AircraftDTO.class)))
                .thenReturn(Optional.of(dto("CS-TUA", "A320", "IN_FLIGHT")));

        Optional<AircraftSummary> result = replicas.findAircraft("CS-TUA");

        assertEquals(Optional.of(summary("CS-TUA", AircraftStatus.IN_FLIGHT)), result);
    }

    @Test
    void findAircraftIsEmptyWhenNoReplicaHasIt() {
        when(aircraftRepository.findById("XX-NONE")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircrafts/XX-NONE"), eq(AircraftDTO.class))).thenReturn(Optional.empty());

        assertTrue(replicas.findAircraft("XX-NONE").isEmpty());
    }

    // ------------------------------------------------------------------ findModel

    @Test
    void findModelPrefersLocalReplicaAndSkipsPeers() {
        when(modelRepository.findById("A320"))
                .thenReturn(Optional.of(new AircraftModel("A320", "Airbus", 180, 24210.0, 6150.0, 840.0)));

        assertEquals("Airbus", replicas.findModel("A320").orElseThrow().manufacturer());
        verifyNoInteractions(peerClient);
    }

    @Test
    void findModelFallsBackToPeerAndEncodesTheName() {
        AircraftModelDTO remote = new AircraftModelDTO();
        remote.setModelName("A320 neo");
        remote.setManufacturer("Airbus");
        remote.setSeatingCapacity(180);
        remote.setFuelCapacity(24210.0);
        remote.setMaximumRange(6500.0);
        remote.setCruisingSpeed(840.0);
        when(modelRepository.findById("A320 neo")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircraft-models/A320%20neo"), eq(AircraftModelDTO.class)))
                .thenReturn(Optional.of(remote));

        Optional<AircraftModelSummary> result = replicas.findModel("A320 neo");

        assertEquals(new AircraftModelSummary("A320 neo", "Airbus", 180, 24210.0, 6500.0, 840.0), result.orElseThrow());
    }

    @Test
    void findModelIsEmptyWhenNoReplicaHasIt() {
        when(modelRepository.findById("B787")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircraft-models/B787"), eq(AircraftModelDTO.class)))
                .thenReturn(Optional.empty());

        assertTrue(replicas.findModel("B787").isEmpty());
    }

    // ------------------------------------------------------------------ withPeers

    @Test
    void withPeersAppendsRemoteAircraft() {
        doReturn(List.of(dto("CS-REM", "B737", "AVAILABLE")))
                .when(peerClient).collectList(eq("/api/aircrafts"), any());

        List<AircraftSummary> merged = replicas.withPeers(List.of(summary("CS-TUA", AircraftStatus.AVAILABLE)),
                "/api/aircrafts");

        assertEquals(List.of("CS-TUA", "CS-REM"),
                merged.stream().map(AircraftSummary::registrationNumber).toList());
    }

    @Test
    void withPeersKeepsTheLocalVersionOnDuplicates() {
        // a mesma matrícula nas duas réplicas: a cópia local (dona) prevalece
        doReturn(List.of(dto("CS-TUA", "A320", "INACTIVE")))
                .when(peerClient).collectList(eq("/api/aircrafts"), any());

        List<AircraftSummary> merged = replicas.withPeers(List.of(summary("CS-TUA", AircraftStatus.AVAILABLE)),
                "/api/aircrafts");

        assertEquals(1, merged.size());
        assertEquals(AircraftStatus.AVAILABLE, merged.get(0).status());
    }

    @Test
    void withPeersDeduplicatesAircraftReportedByMoreThanOnePeer() {
        doReturn(List.of(dto("CS-REM", "B737", "AVAILABLE"), dto("CS-REM", "B737", "AVAILABLE")))
                .when(peerClient).collectList(eq("/api/aircrafts"), any());

        assertEquals(1, replicas.withPeers(List.of(), "/api/aircrafts").size());
    }

    @Test
    void withPeersReturnsLocalDataWhenPeersAreDown() {
        // PeerClient devolve lista vazia quando nenhum peer responde (best-effort)
        doReturn(List.of()).when(peerClient).collectList(eq("/api/aircrafts"), any());

        List<AircraftSummary> local = List.of(summary("CS-TUA", AircraftStatus.AVAILABLE));

        assertEquals(local, replicas.withPeers(local, "/api/aircrafts"));
    }

    @Test
    void withPeersResultIsImmutable() {
        doReturn(List.of()).when(peerClient).collectList(eq("/api/aircrafts"), any());

        List<AircraftSummary> merged = replicas.withPeers(List.of(summary("CS-TUA", AircraftStatus.AVAILABLE)),
                "/api/aircrafts");

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> merged.add(null));
    }

    // ------------------------------------------------------------------ isPeerRequest

    @Test
    void isPeerRequestDelegatesToPeerClient() {
        when(peerClient.isPeerRequest()).thenReturn(true);
        assertTrue(replicas.isPeerRequest());

        when(peerClient.isPeerRequest()).thenReturn(false);
        assertFalse(replicas.isPeerRequest());

        verify(peerClient, org.mockito.Mockito.times(2)).isPeerRequest();
    }
}
