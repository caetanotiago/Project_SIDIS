# AISafe — Distributed Flight Management System (SIDIS 2026/27, Assignment 1)

The PSOFT monolith split into **four microservices**, each running as **three replicas** with its own database, communicating only over **HTTP/REST**.

| Service | Module | WP | Ports (replicas 1/2/3) | Documentation (C4 L3 · L4 · +1) |
|---|---|---|---|---|
| Aircraft Management | [code/aircraftmanagement](code/aircraftmanagement) | WP#1 | 8081 / 8181 / 8281 | [Components](docs/3-components/aircraftmanagement/AircraftManagement-Components.md) · [Code](docs/4-code/aircraftmanagement/AircraftManagement-Code.md) · [Scenarios](docs/5-scenarios/aircraftmanagement/AircraftManagement-Scenarios.md) |
| Maintenance | [code/maintenance](code/maintenance) | WP#4 | 8082 / 8182 / 8282 | [Components](docs/3-components/maintenance/Maintenance-Components.md) · [Code](docs/4-code/maintenance/Maintenance-Code.md) · [Scenarios](docs/5-scenarios/maintenance/Maintenance-Scenarios.md) |
| Airports | [code/airports](code/airports) | WP#2 | 8083 / 8183 / 8283 | [Components](docs/3-components/airports/Airports-Components.md) · [Code](docs/4-code/airports/Airports-Code.md) · [Scenarios](docs/5-scenarios/airports/Airports-Scenarios.md) |
| Flight Routes | [code/flightroutes](code/flightroutes) | WP#3 | 8084 / 8184 / 8284 | [Components](docs/3-components/flightroutes/FlightRoutes-Components.md) · [Code](docs/4-code/flightroutes/FlightRoutes-Code.md) · [Scenarios](docs/5-scenarios/flightroutes/FlightRoutes-Scenarios.md) |
| *(shared library)* | [code/common](code/common) | — | — | [Components](docs/3-components/common/Common-Components.md) · [Code](docs/4-code/common/Common-Code.md) · section 4 below |

System-wide architecture: [docs/README.md](docs/README.md) — **C4+1 model**: [L1 Context](docs/1-context/Context.md) · [L2 Containers](docs/2-containers/Containers.md) (replication, consistency, fault tolerance, security, deployment).

`flightroutes` is complete and is the **reference implementation**: copy its patterns. The other three modules are ready-to-use skeletons (they compile, start and pass a context test).

## 1. Repository structure

```text
code/
├── pom.xml                  aggregator: builds common + the 4 services
├── common/                  shared library (security + distribution), auto-configured in every service
├── aircraftmanagement/      skeleton  ─┐
├── maintenance/             skeleton   ├─ same layout as flightroutes
├── airports/                skeleton  ─┘
├── flightroutes/            complete service (reference)
│   └── src/main/java/isep/psoft/aisafe/
│       ├── <Service>Application.java
│       ├── <service>/ controllers · services · domain · dto · repositories · assemblers · factories · clients
│       ├── infrastructure/ security (<Service>AuthorizationRules) · config (OpenApiConfig)
│       └── exceptions/ GlobalExceptionHandler
│   └── src/main/resources/ application.properties · application-instance1/2/3 · application-tls (· application-demo)
└── scripts/                 run-instance · start-replicas · generate-dev-certs (.sh and .ps1)
docs/                        architecture documentation, C4+1 model (see docs/README.md)
├── 1-context/               L1 — system context
├── 2-containers/            L2 — containers, distribution decisions, deployment, Ports.md
├── 3-components/<service>/  L3 — components of each service
├── 4-code/<service>/        L4 — domain model, class diagrams, design rationale
├── 5-scenarios/<service>/   +1 — user stories, sequence diagrams, tests, Postman
└── _template/               templates for L3, L4 and +1
```

## 2. Build, run and test

Requirements: JDK 21. Maven is not needed (wrapper included). Run from `code/`:

```bash
./mvnw clean install                                  # build everything and run all tests (Windows: .\mvnw.cmd)
./mvnw -pl airports -am install                       # only one service (+ common)

./scripts/start-replicas.sh flightroutes demo         # 3 replicas in background (logs in code/logs/)
.\scripts\start-replicas.ps1 -Service flightroutes -Profiles demo   # Windows: one window per replica
./scripts/run-instance.sh airports 2                  # a single replica (instance2 → port 8183)
./scripts/run-instance.sh flightroutes 1 demo,tls     # HTTPS
```

- Health: `GET http://localhost:<port>/actuator/health` (includes the state of the peers).
- Swagger: `http://localhost:<port>/swagger-ui.html`.
- Token for tests (**development only**): `POST http://localhost:<port>/auth/dev-token` with `{"username":"atcc1","roles":["ATCC"]}` → use `Authorization: Bearer <token>`. Roles: `ADMIN`, `BACKOFFICE_OPERATOR`, `ATCC`, `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR`. The token is valid in every service (shared `jwt.secret`).
- Logs per replica: `code/logs/<service>-N.log` — each line has `[instance] [correlationId]`; the `AUDIT` logger records every request.

## 3. Distributed design (shared by all services)

| Requirement (P1) | How it is implemented |
|---|---|
| Domain-driven segregation | One service per bounded context, one database per service **and per replica**; other services' data only through their REST API (Anti-Corruption Layer in `clients/`). |
| Instance replication | Same JAR, profiles `instance1/2/3` (port, DB, peers). Data is partitioned by the replica that received the write. |
| Peer-to-peer query propagation + aggregation | `PeerClient`: local data first, then the **same GET** to the peers in parallel; merge by id. Peer calls carry `X-Peer-Hops: 1` and are answered with local data only (no loops). |
| Fault tolerance | Timeouts, retry with exponential backoff, circuit breaker per replica, round-robin + failover to other services, partial responses (`X-Partial-Response`), health checks. |
| Consistency | AP with eventual consistency; each record has one owner replica; writes to an existing resource are forwarded to its owner. |
| Security | JWT + role rules per endpoint; `X-Service-Key` between services/replicas; HTTPS with profile `tls`; audit log; correlation id. |

Full explanation (with diagrams) in [docs/2-containers/Containers.md, section 3](docs/2-containers/Containers.md#3-distribution-design-decisions).

## 4. Using the `common` library in your service

Everything below is **auto-configured** (no `@Import` needed); just inject the beans.

**Access rules** — fill in `infrastructure/security/<Service>AuthorizationRules.java`:

```java
auth.requestMatchers(HttpMethod.POST, "/airports").hasRole("BACKOFFICE_OPERATOR")
    .requestMatchers(HttpMethod.GET,  "/airports/**").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR");
```

**Calling another service** (all its replicas, with load balancing, failover, retry, circuit breaker, JWT/service-key propagation) — see `flightroutes/clients/RestClientConfig.java` and `AirportClient.java`:

```java
@Bean
ReplicatedServiceClient flightRoutesService(ReplicatedServiceClientFactory f,
                                            @Value("${services.flightroutes.urls}") List<String> urls) {
    return f.create("Flight Routes", urls);
}
// usage
FlightRouteDTO r = flightRoutesService.call((rest, baseUrl) -> rest.get()
        .uri(baseUrl + "/api/routes/{id}", id)
        .retrieve()
        .onStatus(s -> s.value() == 404, (req, res) -> { throw new EntityNotFoundException("..."); })
        .body(FlightRouteDTO.class));
```

**Answering with the data of all replicas of YOUR service** — inject `PeerClient` (see `flightroutes/services/RouteReplicaQueries.java`):

```java
// GET by id: local first, then the peers
repo.findById(id).map(assembler::toDTO)
    .or(() -> peerClient.findFirst("/airports/" + id, AirportDTO.class))
    .orElseThrow(() -> new EntityNotFoundException("Airport not found: " + id));

// GET collection: local + the same GET on the peers, merged by id
List<AirportDTO> all = new ArrayList<>(local);
peerClient.collectList("/airports?country=PT", new ParameterizedTypeReference<List<AirportDTO>>() {})
          .forEach(dto -> { if (all.stream().noneMatch(a -> a.getIata().equals(dto.getIata()))) all.add(dto); });

// Update of a resource stored in another replica: forward the command to its owner
peerClient.forwardToOwner(HttpMethod.PATCH, "/airports/" + id, dto, AirportDTO.class);
```

Rules: call the peers on **the same endpoint** the client called (same authorization); never forward when `peerClient.isPeerRequest()` (the `PeerClient` already handles it); DTOs exchanged between replicas should have `@JsonIgnoreProperties(ignoreUnknown = true)`; paginate **after** merging.

**Exceptions** — `GlobalExceptionHandler` (already in each skeleton) maps `RemoteServiceUnavailableException` → 503 and the business errors propagated by the owner replica.

## 5. Team conventions

- Main class and packages: `isep.psoft.aisafe.<module>.*` (same layout as `flightroutes`).
- Do not change ports/DB names: they are fixed in [docs/2-containers/Ports.md](docs/2-containers/Ports.md).
- `jwt.secret` and `aisafe.security.service-key` must be equal in every service (defaults are in each `application.properties`; outside development use the variables `JWT_SECRET`, `AISAFE_SERVICE_KEY`, `AISAFE_TLS_PASSWORD`, `AISAFE_DEV_TOKEN=false`).
- Changes to `common` affect everyone: run `./mvnw clean install` (all tests) before committing.
- Documentation follows the **C4+1 model** ([docs/README.md](docs/README.md)): fill in your service's L3/L4/+1 files (already created from [docs/_template](docs/_template)); add your container's calls to the table in [L2 §2](docs/2-containers/Containers.md#2-communication-between-containers). PlantUML sources in `puml/`, rendered SVGs in `svg/` (C4-PlantUML for L1–L3, UML for L4/+1; no Graphviz needed).
- Each service should provide: unit tests, a distributed test like `flightroutes/.../distributed/ReplicaCollaborationTest.java`, and a Postman collection (`docs/5-scenarios/<module>/postman/`).
