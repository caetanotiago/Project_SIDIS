# C4 Level 3 — Airports Service: Components

> Zoom into the **Airports Service** container of [Level 2](../../2-containers/Containers.md): its major building blocks and how they collaborate. One replica is shown; the three replicas are identical.
> Lower levels: [L4 Code](../../4-code/airports/Airports-Code.md) · [+1 Scenarios](../../5-scenarios/airports/Airports-Scenarios.md)

![C4-L3-Airports-Components](svg/C4-L3-Airports-Components.svg)

## 1. Container summary

| Property | Value |
|---|---|
| Bounded context | Airport infrastructure — owns `Airport` (runways, certifications, facilities, status) |
| Replicas | `airports-1` :8083, `airports-2` :8183, `airports-3` :8283 |
| Database | H2 in-memory per replica: `airportsdb-1/2/3` |
| Source code | [code/airports](../../../code/airports) |
| API | `http://localhost:8083/swagger-ui.html` (any replica) |

## 2. Components

| Component | Technology / classes | Responsibility |
|---|---|---|
| **Security & Observability filters** | `common` filters + [`AirportsAuthorizationRules`](../../../code/airports/src/main/java/isep/psoft/aisafe/infrastructure/security/AirportsAuthorizationRules.java) | Correlation id, audit log, `X-Service-Key` check, JWT authentication, role rules per endpoint; any other request to `/api/airports` is denied |
| **Airport Controller** | [`AirportController`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/controllers/AirportController.java) (Spring REST) | `/api/airports` — US106–US109, US106a, US207, US208, US210, US211 |
| **Application services** | [`RegisterAirportService`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/RegisterAirportService.java), [`SearchAirportsService`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/SearchAirportsService.java), [`UpdateAirportService`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/UpdateAirportService.java), [`AirportStatisticsService`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/AirportStatisticsService.java), [`GroupAirportsService`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/GroupAirportsService.java) | One service per group of use cases; transactions; orchestration |
| **Replica queries** | [`AirportReplicaQueries`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/services/AirportReplicaQueries.java) | Combine local data with the data of the peer replicas: first answer by IATA, merge by IATA code (local copy wins), sort |
| **Domain model** | `domain` + `factories` packages | Aggregate `Airport`, Value Objects `Coordinates`, `Runway`, `AirportStatus`, `IataCode`; `AirportFactory` — see [L4](../../4-code/airports/Airports-Code.md) |
| **Repository** | [`AirportRepository`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/repositories/AirportRepository.java) (Spring Data JPA) | Access to the data of **this** replica only |
| **Anti-Corruption Layer** | [`FlightRoutesClient`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/clients/FlightRoutesClient.java), `RestClientConfig` | Route counts per IATA from Flight Routes (contract B5) |
| **PeerClient** *(common)* | [`PeerClient`](../../../code/common/src/main/java/isep/sidis/common/peers/PeerClient.java) | GET to the peer replicas (first answer / aggregation) and command forwarding to the owner replica |
| **ReplicatedServiceClient** *(common)* | `ReplicatedServiceClient` | Round-robin + failover over the replicas of Flight Routes |
| **ResilientExecutor** *(common)* | `ResilientExecutor`, `CircuitBreaker` | Timeouts, retry with exponential backoff, circuit breaker per target |
| *Demo data* | [`DemoDataBootstrap`](../../../code/airports/src/main/java/isep/psoft/aisafe/airports/bootstrap/DemoDataBootstrap.java) (profile `demo`) | Loads a different set of airports in each replica |

The `common` components are described in [common — Components](../common/Common-Components.md).

## 3. Provided interfaces (REST API)

Every endpoint is served by every replica; **every GET returns the data of all replicas**. IATA codes in paths are case-insensitive (normalised to upper case); an invalid code gives `400`.

| Method | Endpoint | US | Input | Output | Roles |
|---|---|---|---|---|---|
| POST | `/api/airports` | US106, US207 | `CreateAirportDTO` (iataCode, name, city, country, region?, timezone, latitude, longitude, runways[], terminals?, gates?, services?, photoUrls?, operationalHours?, contactEmail?, contactPhone?) | `201` `AirportDTO` + `Location` | BACKOFFICE_OPERATOR |
| GET | `/api/airports/{iataCode}` | US107 (contract B2) | IATA code | `200` `AirportDTO` | BACKOFFICE_OPERATOR, ATCC |
| GET | `/api/airports?city=&country=&name=` | US108 | optional filters (city/country exact, name contains; case-insensitive) | `200` `List<AirportDTO>` sorted by IATA | ATCC |
| POST | `/api/airports/{iataCode}/certifications` | US106a | `AddCertificationDTO` (aircraftModel) | `201` `AirportDTO` | BACKOFFICE_OPERATOR, ATCC |
| PATCH | `/api/airports/{iataCode}/status` | US109 | `UpdateAirportStatusDTO` (`OPERATIONAL` / `CLOSED` / `UNDER_MAINTENANCE`) | `200` `AirportDTO` | BACKOFFICE_OPERATOR |
| PATCH | `/api/airports/{iataCode}` | US208 | `UpdateAirportDetailsDTO` (any of operationalHours, contactEmail, contactPhone, terminals, gates, services, photoUrls) | `200` `AirportDTO` | BACKOFFICE_OPERATOR |
| GET | `/api/airports/statistics/busiest?limit=` | US210 | `limit` 1–100 (default 5) | `200` `List<BusiestAirportDTO>` (rank, iataCode, name, city, country, routeCount) | BACKOFFICE_OPERATOR |
| GET | `/api/airports/grouped?by=` | US211 | `country` (default) / `region` | `200` `List<AirportGroupDTO>` (group, count, airports) | ATCC |

US209 (routes from/to an airport) is served by Flight Routes: `GET /api/routes/by-airport/{iataCode}`.

Infrastructure endpoints (from `common`): `GET /actuator/health`, `POST /auth/dev-token` (development only), `/swagger-ui.html`.

**Errors** — body `{ "error": "<message>" }` (Bean Validation: `{ "<field>": "<message>" }`):

| Exception | HTTP |
|---|---|
| `IllegalArgumentException` (invalid IATA, timezone, status, grouping, limit…), `MethodArgumentNotValidException` | 400 |
| `EntityNotFoundException` (no replica has the airport) | 404 |
| `IllegalStateException` (IATA already registered, model already certified), `ObjectOptimisticLockingFailureException` | 409 |
| `RemoteServiceUnavailableException` (no peer / no Flight Routes replica answered) | 503 |
| `ResponseStatusException` (remote 401/403, business error of the owner replica) | same status |

## 4. Required interfaces

| Component | Calls | Endpoint |
|---|---|---|
| `FlightRoutesClient` | Flight Routes (all replicas) | `GET /api/routes/statistics/count-by-airport` (B5) |
| `PeerClient` | Airports peer replicas | the same endpoint as the incoming request, `X-Peer-Hops: 1` |

## 5. How the components realise the distribution requirements

| Requirement | Components involved |
|---|---|
| Peer forwarding of GET by IATA (US107, B2, uniqueness check of US106, details of US210) | services → `AirportReplicaQueries.findByIata` → `PeerClient.findFirst` |
| Aggregation of collections (US108, US211) | `SearchAirportsService` → `AirportReplicaQueries.withPeers` → `PeerClient.collectList`, merged by IATA; US211 groups after merging |
| Statistics from another service (US210) | `AirportStatisticsService` → `FlightRoutesClient` → `ReplicatedServiceClient` (Flight Routes already sums its replicas) |
| Single owner per airport (US106a, US109, US208) | `UpdateAirportService.onOwner` → `PeerClient.forwardToOwner` (unicast, one peer at a time) |
| Concurrency inside the owner | `@Version` on `Airport` + `saveAndFlush` → `409` |
| Calls to other services with failover | ACL → `ReplicatedServiceClient` → `ResilientExecutor` |
| Security and audit | filter chain of `common` + `AirportsAuthorizationRules` |

The system-wide decisions (consistency model, fault tolerance, security) are in [L2 §3](../../2-containers/Containers.md#3-distribution-design-decisions).
