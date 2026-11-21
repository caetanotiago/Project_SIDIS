package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.AirportsApplication;
import isep.psoft.aisafe.airports.command.AirportStateRepublisher;
import isep.psoft.aisafe.airports.command.IntegrationEventTransport;
import isep.psoft.aisafe.airports.command.OutgoingIntegrationEvent;
import isep.psoft.aisafe.airports.command.ReadModelSynchronizer;
import isep.psoft.aisafe.airports.command.RegisterAirportService;
import isep.psoft.aisafe.airports.command.UpdateAirportService;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.dto.RunwayDTO;
import isep.psoft.aisafe.airports.dto.UpdateAirportStatusDTO;
import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.events.AirportSyncRequest;
import isep.psoft.aisafe.airports.events.external.FlightRouteEventPayload;
import isep.psoft.aisafe.airports.query.AirportProjection;
import isep.psoft.aisafe.airports.query.ReplicatedAirport;
import isep.psoft.aisafe.airports.query.ReplicatedAirportRepository;
import isep.psoft.aisafe.airports.query.external.RouteCountProjection;
import isep.psoft.aisafe.infrastructure.messaging.AirportsEventsListener;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.EventPublisher;
import isep.sidis.common.messaging.IntegrationEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CQRS do Airports numa réplica real (com a sua H2 em memória), SEM broker. Os eventos que o lado de escrita
 * publica depois do commit são capturados por um transporte de teste; os eventos "recebidos" são entregues
 * diretamente às projeções, tal como fazem os listeners do RabbitMQ. Cobre:
 * <ul>
 *   <li>projeção do read model: criação, idempotência, versões antigas, payload inválido, tipo desconhecido;</li>
 *   <li>lado de escrita: read-your-writes na réplica dona, um evento por comando, nada publicado em rollback;</li>
 *   <li>sincronização no arranque/a pedido: republicação dos aeroportos próprios, pedido próprio ignorado;</li>
 *   <li>listener: conversão do formato JSON "do fio" e encaminhamento pelo eventType;</li>
 *   <li>contagem de rotas por aeroporto (US210) a partir dos eventos do Flight Routes;</li>
 *   <li>com as mensagens desligadas (omissão nos testes) não existe nenhum bean AMQP.</li>
 * </ul>
 */
class AirportCqrsTest {

    private static final String SELF = "it-cqrs";
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    /** Transporte de teste: guarda os eventos que a réplica publicaria no RabbitMQ (só depois do commit). */
    private static final List<OutgoingIntegrationEvent> PUBLISHED = new CopyOnWriteArrayList<>();
    private static final IntegrationEventTransport CAPTURE = PUBLISHED::add;

    private static ConfigurableApplicationContext replica;
    private static AirportProjection projection;
    private static ReplicatedAirportRepository readModel;

    @BeforeAll
    static void start() {
        replica = new SpringApplicationBuilder(AirportsApplication.class)
                .initializers(c -> c.getBeanFactory().registerSingleton("captureTransport", CAPTURE))
                .run("--server.port=0",
                        "--aisafe.instance-id=" + SELF,
                        "--spring.datasource.url=jdbc:h2:mem:airports-cqrs;DB_CLOSE_DELAY=-1",
                        "--aisafe.peers.urls=http://localhost:1", // nenhum peer real: tudo é respondido localmente
                        "--aisafe.bootstrap.enabled=false",
                        "--logging.file.name=target/it-logs/airports-cqrs.log");
        projection = replica.getBean(AirportProjection.class);
        readModel = replica.getBean(ReplicatedAirportRepository.class);
    }

    @AfterAll
    static void stop() {
        if (replica != null) replica.close();
    }

    @BeforeEach
    void clearCaptured() {
        PUBLISHED.clear();
    }

    // ------------------------------------------------------------------ projeção do read model

    @Test
    void airportCreatedFromAnotherReplicaIsStoredInTheReadModel() {
        assertTrue(projection.apply(event(AirportEvents.AIRPORT_CREATED, payload("FAO", "OPERATIONAL", 0L), "airports-9")));

        ReplicatedAirport fao = readModel.findById("FAO").orElseThrow();
        assertEquals("Faro", fao.getCity());
        assertEquals("OPERATIONAL", fao.getStatus());
        assertEquals(0L, fao.getSourceVersion());
        assertEquals("airports-9", fao.getOwnerInstance());
        assertEquals(1, fao.getRunways().size());
        assertEquals(Set.of("A320"), fao.getCertifiedModels());
    }

    @Test
    void theSameEventDeliveredTwiceIsAppliedOnlyOnce() {
        IntegrationEvent<AirportEventPayload> created = event(AirportEvents.AIRPORT_CREATED, payload("BCN", "OPERATIONAL", 0L), "airports-9");
        assertTrue(projection.apply(created));

        // Entretanto chega uma versão mais recente; depois o broker reentrega o evento antigo (at-least-once)
        assertTrue(projection.apply(event(AirportEvents.AIRPORT_STATUS_CHANGED, payload("BCN", "CLOSED", 1L), "airports-9")));
        assertFalse(projection.apply(created));

        assertEquals("CLOSED", readModel.findById("BCN").orElseThrow().getStatus());
    }

    @Test
    void olderVersionsAreIgnored() {
        assertTrue(projection.apply(event(AirportEvents.AIRPORT_UPDATED, payload("AMS", "UNDER_MAINTENANCE", 5L), "airports-9")));
        // Evento diferente (outro eventId) mas com versão antiga, ex. reenviado a partir da DLQ
        projection.apply(event(AirportEvents.AIRPORT_UPDATED, payload("AMS", "OPERATIONAL", 3L), "airports-9"));

        ReplicatedAirport ams = readModel.findById("AMS").orElseThrow();
        assertEquals("UNDER_MAINTENANCE", ams.getStatus());
        assertEquals(5L, ams.getSourceVersion());
    }

    @Test
    void anEventWithoutIataCodeFailsSoItIsRetriedAndDeadLettered() {
        IntegrationEvent<AirportEventPayload> broken = event(AirportEvents.AIRPORT_CREATED, payload(null, "OPERATIONAL", 0L), "airports-9");
        assertThrows(IllegalArgumentException.class, () -> projection.apply(broken));
    }

    @Test
    void unknownEventTypesAreIgnored() {
        assertTrue(projection.apply(event("AirportRenamed", payload("ORY", "OPERATIONAL", 0L), "airports-9")));
        assertTrue(readModel.findById("ORY").isEmpty());
    }

    // ------------------------------------------------------------------ lado de escrita

    @Test
    void theOwnerReadsItsOwnWriteImmediatelyAndPublishesOneEventPerCommandAfterTheCommit() {
        replica.getBean(RegisterAirportService.class).register(createDto("OPO"));

        // Read-your-writes: o read model local foi atualizado na mesma transação do write model
        ReplicatedAirport opo = readModel.findById("OPO").orElseThrow();
        assertEquals(SELF, opo.getOwnerInstance());
        assertEquals("OPERATIONAL", opo.getStatus());

        assertEquals(1, PUBLISHED.size());
        OutgoingIntegrationEvent created = PUBLISHED.get(0);
        assertEquals("airport.created", created.routingKey());
        assertEquals(AirportEvents.AIRPORT_CREATED, created.event().eventType());
        assertEquals("OPO", created.event().aggregateId());
        assertEquals(SELF, created.event().sourceInstance());

        replica.getBean(UpdateAirportService.class).changeStatus("opo", new UpdateAirportStatusDTO("CLOSED"));

        assertEquals("CLOSED", readModel.findById("OPO").orElseThrow().getStatus());
        assertEquals(1L, readModel.findById("OPO").orElseThrow().getSourceVersion());
        OutgoingIntegrationEvent statusChanged = PUBLISHED.get(1);
        assertEquals("airport.status-changed", statusChanged.routingKey());
        AirportEventPayload p = (AirportEventPayload) statusChanged.event().payload();
        // Estado completo no evento (state-carrying), incluindo os campos que o Flight Routes lê
        assertEquals("CLOSED", p.status());
        assertEquals(1L, p.version());
        assertNotNull(p.latitude());
        assertNotNull(p.longitude());
    }

    @Test
    void aFailedCommandPublishesNothing() {
        replica.getBean(RegisterAirportService.class).register(createDto("LIS"));
        PUBLISHED.clear();

        // Duplicado: a transação falha, por isso nenhum evento sai
        assertThrows(IllegalStateException.class,
                () -> replica.getBean(RegisterAirportService.class).register(createDto("LIS")));
        assertTrue(PUBLISHED.isEmpty());
    }

    // ------------------------------------------------------------------ sincronização

    @Test
    void republishingAnnouncesEveryOwnedAirportAsAirportUpdated() {
        replica.getBean(RegisterAirportService.class).register(createDto("MAD"));
        PUBLISHED.clear();

        int owned = replica.getBean(AirportStateRepublisher.class).republishOwnedAirports("test");

        assertEquals(owned, PUBLISHED.size());
        assertTrue(owned >= 1);
        assertTrue(PUBLISHED.stream().allMatch(o -> o.routingKey().equals("airport.updated")
                && o.event().eventType().equals(AirportEvents.AIRPORT_UPDATED)
                && o.event().sourceInstance().equals(SELF)));
        assertTrue(PUBLISHED.stream().anyMatch(o -> o.event().aggregateId().equals("MAD")));
    }

    @Test
    void aSyncRequestFromThisReplicaIsIgnoredAndOneFromAnotherIsAnswered() {
        replica.getBean(RegisterAirportService.class).register(createDto("CDG"));
        ReadModelSynchronizer synchronizer = replica.getBean(ReadModelSynchronizer.class);
        PUBLISHED.clear();

        synchronizer.onSyncRequested(SELF);
        assertTrue(PUBLISHED.isEmpty());

        synchronizer.onSyncRequested("airports-2");
        assertFalse(PUBLISHED.isEmpty());
    }

    // ------------------------------------------------------------------ listener (formato do fio)

    @Test
    void theListenerConvertsTheWireFormatAndRoutesByEventType() {
        AirportsEventsListener listener = new AirportsEventsListener(projection, replica.getBean(ReadModelSynchronizer.class));

        listener.on(overTheWire(event(AirportEvents.AIRPORT_CREATED, payload("FNC", "OPERATIONAL", 0L), "airports-3")));
        assertEquals("Faro", readModel.findById("FNC").orElseThrow().getCity());

        replica.getBean(RegisterAirportService.class).register(createDto("LHR"));
        PUBLISHED.clear();
        IntegrationEvent<AirportSyncRequest> sync = IntegrationEvent.create(AirportEvents.SYNC_REQUESTED, Instant.now(),
                "airports", "airports-3", "airports-3", new AirportSyncRequest("airports-3"));
        listener.on(overTheWire(sync));
        assertTrue(PUBLISHED.stream().anyMatch(o -> o.event().aggregateId().equals("LHR")));
    }

    // ------------------------------------------------------------------ US210: rotas do Flight Routes

    @Test
    void routeCountsComeFromFlightRoutesEventsWithoutDoubleCounting() {
        RouteCountProjection routes = replica.getBean(RouteCountProjection.class);
        IntegrationEvent<FlightRouteEventPayload> created = routeEvent("RouteCreated", "r-1", "OPO", "LIS", 0L);
        assertTrue(routes.apply(created));
        assertTrue(routes.apply(routeEvent("RouteCreated", "r-2", "LIS", "MAD", 0L)));
        // Duplicado e atualização da mesma rota: continua a contar uma vez
        assertFalse(routes.apply(created));
        assertTrue(routes.apply(routeEvent("RouteUpdated", "r-1", "OPO", "LIS", 1L)));
        // Tipo que não interessa ao Airports
        assertTrue(routes.apply(routeEvent("ScheduledFlightCreated", "f-1", "OPO", "LIS", 0L)));

        Map<String, Long> counts = routes.routeCountsByAirport();
        assertEquals(2L, counts.get("LIS"));
        assertEquals(1L, counts.get("OPO"));
        assertEquals(1L, counts.get("MAD"));
    }

    // ------------------------------------------------------------------ mensagens desligadas

    @Test
    void withMessagingOffThereIsNoBrokerBean() {
        assertTrue(replica.getBeansOfType(EventPublisher.class).isEmpty());
        assertTrue(replica.getBeansOfType(AirportsEventsListener.class).isEmpty());
    }

    // ------------------------------------------------------------------ auxiliares

    private static IntegrationEvent<AirportEventPayload> event(String type, AirportEventPayload payload, String from) {
        String id = payload.iataCode() == null ? "unknown" : payload.iataCode();
        return IntegrationEvent.create(type, Instant.now(), "airports", from, id, payload);
    }

    private static IntegrationEvent<FlightRouteEventPayload> routeEvent(String type, String id, String origin,
                                                                       String destination, long version) {
        return IntegrationEvent.create(type, Instant.now(), "flightroutes", "flightroutes-1", id,
                new FlightRouteEventPayload(id, origin, destination, "ACTIVE", version));
    }

    /** Serializa e volta a ler o envelope como o listener o recebe do RabbitMQ (payload em árvore JSON). */
    private static IntegrationEvent<JsonNode> overTheWire(IntegrationEvent<?> event) {
        return JSON.readValue(JSON.writeValueAsString(event), new TypeReference<IntegrationEvent<JsonNode>>() { });
    }

    private static AirportEventPayload payload(String iata, String status, Long version) {
        return new AirportEventPayload(iata, "Faro Airport", "Faro", "Portugal", "Europe", "Europe/Lisbon",
                37.0144, -7.9659, status, List.of(new AirportEventPayload.RunwayData("10/28", 2490, 100, "ASPHALT")),
                Set.of("A320"), 1, 16, Set.of("WIFI"), List.of(), "05:00-24:00", null, null, version);
    }

    private static CreateAirportDTO createDto(String iata) {
        return new CreateAirportDTO(iata, iata + " Airport", "City " + iata, "Country", "Europe", "Europe/Lisbon",
                40.0, -8.0, List.of(new RunwayDTO("17/35", 3000, 170, "ASPHALT")),
                1, 10, Set.of(), List.of(), null, null, null);
    }
}
