package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US103 (ponte B1a) e US104 (pesquisa com filtros). */
@ExtendWith(MockitoExtension.class)
class SearchAircraftsServiceTest {

    @Mock private AircraftReplicaQueries replicas;
    @Mock private AircraftRepository repository;

    private SearchAircraftsService service;

    private final Aircraft tua = new Aircraft("CS-TUA", "A320", 180, LocalDate.of(2020, 1, 10), AircraftStatus.AVAILABLE);
    private final Aircraft tub = new Aircraft("CS-TUB", "A320", 180, LocalDate.of(2018, 6, 1), AircraftStatus.IN_FLIGHT);
    private final Aircraft tnb = new Aircraft("CS-TNB", "B737", 160, LocalDate.of(2020, 12, 31), AircraftStatus.AVAILABLE);
    private final Aircraft noDate = new Aircraft("CS-TXX", "B737", 160, null, AircraftStatus.INACTIVE);

    @BeforeEach
    void setUp() {
        service = new SearchAircraftsService(replicas, repository);
    }

    private static List<String> registrations(List<AircraftSummary> list) {
        return list.stream().map(AircraftSummary::registrationNumber).toList();
    }

    /** Pedido vindo de um peer: só dados locais, sem consultar outras réplicas. */
    private void asPeerRequest() {
        when(replicas.isPeerRequest()).thenReturn(true);
    }

    @Test
    void getByRegistrationReturnsAircraftFromReplicas() {
        AircraftSummary s = tua.toSummary();
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.of(s));

        assertEquals(s, service.getByRegistration("CS-TUA"));
    }

    @Test
    void getByRegistrationThrowsNotFoundWhenMissing() {
        when(replicas.findAircraft("XX-NONE")).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> service.getByRegistration("XX-NONE"));

        assertEquals("Aircraft not found: XX-NONE", ex.getMessage());
    }

    @Test
    void searchWithoutFiltersReturnsEverythingSortedByRegistration() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tub, tua, tnb));

        assertEquals(List.of("CS-TNB", "CS-TUA", "CS-TUB"), registrations(service.search(null, null, null)));
    }

    @Test
    void blankFiltersAreTreatedAsAbsent() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua, tnb));

        assertEquals(2, service.search("  ", "   ", null).size());
    }

    @Test
    void filtersByModelName() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua, tub, tnb));

        assertEquals(List.of("CS-TUA", "CS-TUB"), registrations(service.search("A320", null, null)));
    }

    @Test
    void filtersByStatusIgnoringCase() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua, tub, tnb));

        assertEquals(List.of("CS-TUB"), registrations(service.search(null, "in_flight", null)));
    }

    @Test
    void filtersByManufacturingYearAndIgnoresAircraftWithoutDate() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua, tub, tnb, noDate));

        assertEquals(List.of("CS-TNB", "CS-TUA"), registrations(service.search(null, null, 2020)));
    }

    @Test
    void combinesFiltersWithAnd() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua, tub, tnb));

        assertEquals(List.of("CS-TUA"), registrations(service.search("A320", "AVAILABLE", 2020)));
    }

    @Test
    void noMatchReturnsEmptyList() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua));

        assertTrue(service.search("B787", null, null).isEmpty());
    }

    @Test
    void invalidStatusFilterThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.search(null, "FLYING", null));

        verify(repository, never()).findAll();
    }

    @Test
    void peerRequestDoesNotQueryOtherReplicas() {
        asPeerRequest();
        when(repository.findAll()).thenReturn(List.of(tua));

        service.search(null, null, null);

        verify(replicas, never()).withPeers(any(), anyString());
    }

    @Test
    void clientRequestMergesPeerResultsAndSortsThem() {
        AircraftSummary remote = new AircraftSummary("CS-AAA", "B737", 160, LocalDate.of(2019, 3, 3),
                AircraftStatus.AVAILABLE);
        when(replicas.isPeerRequest()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(tua));
        when(replicas.withPeers(any(), anyString()))
                .thenReturn(List.of(tua.toSummary(), remote));

        List<AircraftSummary> result = service.search(null, null, null);

        assertEquals(List.of("CS-AAA", "CS-TUA"), registrations(result));
    }

    @Test
    void clientRequestForwardsTheFiltersToThePeers() {
        when(replicas.isPeerRequest()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(tua));
        when(replicas.withPeers(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        service.search("A320", "available", 2020);

        ArgumentCaptor<String> uri = ArgumentCaptor.forClass(String.class);
        verify(replicas).withPeers(eq(List.of(tua.toSummary())), uri.capture());
        String sent = uri.getValue();
        assertTrue(sent.startsWith("/api/aircrafts?"), sent);
        assertTrue(sent.contains("modelName=A320"), sent);
        assertTrue(sent.contains("status=AVAILABLE"), sent); // normalizado para o valor do enum
        assertTrue(sent.contains("manufacturingYear=2020"), sent);
    }

    @Test
    void clientRequestWithoutFiltersCallsPeersWithBarePath() {
        when(replicas.isPeerRequest()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of());
        when(replicas.withPeers(any(), anyString())).thenReturn(List.of());

        service.search(null, null, null);

        verify(replicas).withPeers(List.of(), "/api/aircrafts");
    }
}
