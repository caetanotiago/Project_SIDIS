package isep.psoft.aisafe.distributed;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.MinutesByAircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries;
import isep.psoft.aisafe.aircraftmanagement.services.CreateAircraftService;
import isep.psoft.aisafe.aircraftmanagement.services.SearchAircraftsService;
import isep.psoft.aisafe.aircraftmanagement.services.TopUtilizedModelsService;
import isep.psoft.aisafe.aircraftmanagement.services.UpdateAircraftStatusService;
import isep.sidis.common.peers.PeerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cenários distribuídos: serviços reais + AircraftReplicaQueries real, com repositórios e PeerClient simulados
 * (a réplica "local" é o repositório, a(s) outra(s) réplica(s) é o PeerClient).
 */
@ExtendWith(MockitoExtension.class)
class DistributedScenariosTest {

    private static final LocalDate DATE = LocalDate.of(2020, 1, 1);

    @Mock private AircraftRepository aircraftRepository;
    @Mock private AircraftModelRepository modelRepository;
    @Mock private PeerClient peerClient;
    @Mock private FlightRoutesClient flightRoutesClient;

    private AircraftAssembler assembler;
    private AircraftReplicaQueries replicas;

    @BeforeEach
    void setUp() {
        assembler = new AircraftAssembler();
        replicas = new AircraftReplicaQueries(aircraftRepository, modelRepository, peerClient, assembler);
    }

    private static AircraftDTO remoteAircraft(String reg, String model, String status) {
        AircraftDTO d = new AircraftDTO();
        d.setRegistrationNumber(reg);
        d.setModelName(model);
        d.setSeatingCapacity(160);
        d.setManufacturingDate(DATE);
        d.setStatus(status);
        return d;
    }

    private static CreateAircraftDTO createDto() {
        CreateAircraftDTO dto = new CreateAircraftDTO();
        dto.setRegistrationNumber("CS-TUA");
        dto.setModelName("B737");
        dto.setSeatingCapacity(160);
        dto.setManufacturingDate(DATE);
        return dto;
    }

    // ------------------------------------------------------------------ criação

    @Test
    void createAircraftWhoseModelLivesInAnotherReplica() {
        AircraftModelDTO remoteModel = new AircraftModelDTO();
        remoteModel.setModelName("B737");
        remoteModel.setManufacturer("Boeing");
        remoteModel.setSeatingCapacity(160);
        remoteModel.setFuelCapacity(26000.0);
        remoteModel.setMaximumRange(5400.0);
        remoteModel.setCruisingSpeed(830.0);
        when(aircraftRepository.findById("CS-TUA")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircrafts/CS-TUA"), eq(AircraftDTO.class))).thenReturn(Optional.empty());
        when(modelRepository.findById("B737")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircraft-models/B737"), eq(AircraftModelDTO.class)))
                .thenReturn(Optional.of(remoteModel));
        when(aircraftRepository.save(any(Aircraft.class))).thenAnswer(inv -> inv.getArgument(0));

        Aircraft created = new CreateAircraftService(aircraftRepository, replicas).create(createDto());

        assertEquals("CS-TUA", created.getRegistrationNumber());
        verify(aircraftRepository).save(created); // fica guardada na réplica que recebeu o POST
    }

    @Test
    void createAircraftWithRegistrationAlreadyUsedInAnotherReplicaIsRejected() {
        when(aircraftRepository.findById("CS-TUA")).thenReturn(Optional.empty());
        when(peerClient.findFirst(eq("/api/aircrafts/CS-TUA"), eq(AircraftDTO.class)))
                .thenReturn(Optional.of(remoteAircraft("CS-TUA", "B737", "AVAILABLE")));

        assertThrows(IllegalStateException.class,
                () -> new CreateAircraftService(aircraftRepository, replicas).create(createDto()));

        verify(aircraftRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ pesquisa agregada

    @Test
    void searchAggregatesLocalAndRemoteAircraftSortedByRegistration() {
        SearchAircraftsService search = new SearchAircraftsService(replicas, aircraftRepository);
        when(peerClient.isPeerRequest()).thenReturn(false);
        when(aircraftRepository.findAll()).thenReturn(List.of(
                new Aircraft("CS-TUB", "A320", 180, DATE, AircraftStatus.AVAILABLE),
                new Aircraft("CS-TUA", "A320", 180, DATE, AircraftStatus.AVAILABLE)));
        doReturn(List.of(remoteAircraft("CS-AAA", "B737", "AVAILABLE"), remoteAircraft("CS-TUB", "A320", "INACTIVE")))
                .when(peerClient).collectList(anyString(), any());

        List<AircraftSummary> result = search.search(null, null, null);

        assertEquals(List.of("CS-AAA", "CS-TUA", "CS-TUB"),
                result.stream().map(AircraftSummary::registrationNumber).toList());
        // CS-TUB existe nas duas réplicas: prevalece a cópia local
        assertEquals(AircraftStatus.AVAILABLE, result.get(2).status());
    }

    @Test
    void searchTriggeredByAPeerAnswersWithLocalDataOnly() {
        SearchAircraftsService search = new SearchAircraftsService(replicas, aircraftRepository);
        when(peerClient.isPeerRequest()).thenReturn(true);
        when(aircraftRepository.findAll()).thenReturn(List.of(
                new Aircraft("CS-TUA", "A320", 180, DATE, AircraftStatus.AVAILABLE)));

        List<AircraftSummary> result = search.search(null, null, null);

        assertEquals(1, result.size());
        verify(peerClient, never()).collectList(anyString(), any()); // evita ciclos de pedidos entre réplicas
    }

    // ------------------------------------------------------------------ escrita no dono

    @Test
    void statusChangeOfARemoteAircraftIsForwardedToItsOwner() {
        UpdateAircraftStatusService service =
                new UpdateAircraftStatusService(aircraftRepository, peerClient, assembler);
        UpdateAircraftStatusDTO body = new UpdateAircraftStatusDTO("UNDER_MAINTENANCE");
        AircraftDTO updatedByOwner = remoteAircraft("CS-REM", "B737", "UNDER_MAINTENANCE");
        when(aircraftRepository.findById("CS-REM")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircrafts/CS-REM/status"),
                eq(body), eq(AircraftDTO.class))).thenReturn(Optional.of(updatedByOwner));

        AircraftDTO result = service.updateStatus("CS-REM", body);

        assertEquals("UNDER_MAINTENANCE", result.getStatus());
        verify(aircraftRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ estatísticas entre réplicas

    @Test
    void topUtilizedModelsCombinesFleetFromAllReplicasWithFlightMinutes() {
        SearchAircraftsService search = new SearchAircraftsService(replicas, aircraftRepository);
        TopUtilizedModelsService top = new TopUtilizedModelsService(search, flightRoutesClient);
        when(peerClient.isPeerRequest()).thenReturn(false);
        when(aircraftRepository.findAll()).thenReturn(List.of(
                new Aircraft("CS-TUA", "A320", 180, DATE, AircraftStatus.AVAILABLE)));
        doReturn(List.of(remoteAircraft("CS-REM", "B737", "AVAILABLE")))
                .when(peerClient).collectList(anyString(), any());
        when(flightRoutesClient.minutesByAircraft())
                .thenReturn(new MinutesByAircraft(Map.of("CS-TUA", 60L, "CS-REM", 120L), false));

        List<ModelUtilizationDTO> rows = top.topUtilized(5).rows();

        // o B737 só existe na réplica remota e, ainda assim, lidera o ranking
        assertEquals(List.of("B737", "A320"), rows.stream().map(ModelUtilizationDTO::modelName).toList());
        assertEquals(120L, rows.get(0).totalMinutes());
    }
}
