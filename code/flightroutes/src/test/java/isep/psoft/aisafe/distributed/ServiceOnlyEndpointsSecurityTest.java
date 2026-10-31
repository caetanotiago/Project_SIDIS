package isep.psoft.aisafe.distributed;

import isep.psoft.aisafe.FlightRoutesApplication;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoints que só outros serviços AISafe consomem (Q11, {@code aisafe.security.service-only-paths} do
 * application.properties) e o acesso do Backoffice ao US204 (Q4).
 *
 * <p>Arranca UMA réplica real, sem peers: a chave de serviço é verificada antes do JWT, e os roles
 * continuam a ser verificados depois de a chave ser aceite.
 */
class ServiceOnlyEndpointsSecurityTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext replica;
    private static String url;
    private static String atcc;
    private static String backoffice;
    private static String technician;

    @BeforeAll
    static void startReplica() throws IOException {
        int port = freePort();
        url = "http://localhost:" + port;
        String nobody = "http://localhost:" + freePort(); // serviços remotos não usados nestes testes
        replica = new SpringApplicationBuilder(FlightRoutesApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=it-service-only",
                "--spring.datasource.url=jdbc:h2:mem:it-service-only;DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=",
                "--services.aircraft.urls=" + nobody,
                "--services.airports.urls=" + nobody,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/service-only.log");
        JwtTokenProvider tokens = replica.getBean(JwtTokenProvider.class);
        atcc = tokens.generateToken("atcc-it", List.of("ROLE_ATCC"));
        backoffice = tokens.generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        technician = tokens.generateToken("tech-it", List.of("ROLE_MAINTENANCE_TECHNICIAN"));
    }

    @AfterAll
    static void stop() {
        if (replica != null) replica.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/routes/statistics/count-by-airport",
            "/api/routes/compatible?range=5000&capacity=150",
            "/api/scheduled-flights/statistics/minutes-by-aircraft",
            "/api/scheduled-flights/operational-hours",
            "/api/scheduled-flights/statistics/usage-by-route"})
    void serviceOnlyEndpointsRejectAValidJwtWithoutServiceKey(String path) throws Exception {
        HttpResponse<String> response = get(path, atcc, null);

        assertEquals(401, response.statusCode(), response.body());
        assertTrue(response.body().contains("only available to AISafe services"), response.body());
    }

    @Test
    void wrongServiceKeyIsRejected() throws Exception {
        assertEquals(401, get("/api/routes/statistics/count-by-airport", backoffice, "wrong").statusCode());
    }

    @Test
    void validServiceKeyStillGoesThroughTheRoleCheck() throws Exception {
        // count-by-airport (B5) é só para BACKOFFICE_OPERATOR
        assertEquals(200, get("/api/routes/statistics/count-by-airport", backoffice, SERVICE_KEY).statusCode());
        assertEquals(403, get("/api/routes/statistics/count-by-airport", atcc, SERVICE_KEY).statusCode());
        // a chave não substitui o JWT
        assertEquals(401, get("/api/routes/statistics/count-by-airport", null, SERVICE_KEY).statusCode());
    }

    @Test
    void minutesByAircraftAllowsBackofficeAndAtccForUs204() throws Exception {
        String path = "/api/scheduled-flights/statistics/minutes-by-aircraft";
        assertEquals(200, get(path, backoffice, SERVICE_KEY).statusCode());
        assertEquals(200, get(path, atcc, SERVICE_KEY).statusCode());
        assertEquals(403, get(path, technician, SERVICE_KEY).statusCode());
    }

    @Test
    void backofficeIsStillDeniedOnTheOtherScheduledFlightEndpoints() throws Exception {
        assertEquals(403, get("/api/scheduled-flights/search?aircraft=CS-TUA", backoffice, null).statusCode());
        assertEquals(403, get("/api/scheduled-flights/operational-hours", backoffice, SERVICE_KEY).statusCode());
    }

    @Test
    void userFacingEndpointsDoNotNeedTheServiceKey() throws Exception {
        assertEquals(200, get("/api/routes/search", atcc, null).statusCode());
    }

    private static HttpResponse<String> get(String path, String token, String serviceKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (serviceKey != null) b.header(AisafeHeaders.SERVICE_KEY, serviceKey);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
