# Integration Contracts (L2)

This is the reference for the REST calls **between** AISafe services. If you implement a service that another service calls, implement exactly what is written here. If you call another service, expect exactly this.

Every statement about existing code was taken from the code and has a **Source** line. All bridges are implemented on both sides, except B4a, which has no consumer. The decisions are closed (section 7); what the code does not do is listed in **Known limitations** (section 8).

**Assignment 2.** The REST contracts below are unchanged for external clients and still in force between services. What changed, and the asynchronous **event contracts** (envelope, exchanges, routing keys and queues, the event catalogue, how to publish and consume, and the Assignment 2 status of every bridge) are in [section 9](#9-assignment-2--event-contracts). Reliability, consistency and the known limitations of messaging are in [Containers §3.3–3.6](Containers.md#33-consistency-model-cap).

> FR = `code/flightroutes/src/main/java/isep/psoft/aisafe` · COMMON = `code/common/src/main/java/isep/sidis/common`

---

## 1. Purpose and status legend

| Status | Meaning |
|---|---|
| `IMPLEMENTED` | Provider and consumer both exist in code. |
| `IMPLEMENTED (provider only)` | The provider endpoint exists in code; no other service calls it. |
| `CONSUMER-ONLY` | The consumer exists in code; the **provider must implement exactly this contract**. |
| `PROPOSED` | Agreed design; nothing implemented on either side. |

Assignment 2 status of a bridge (section 9):

| Status | Meaning |
|---|---|
| `EVENT-BASED` | The consumer no longer calls the provider: it reads a local read model fed by the provider's events. |
| `HTTP (waiting for <service> events)` | Still a synchronous call; the consumer side of the event-based version is ready, the provider does not publish the events yet. |
| `HTTP (provider uses read model)` | Still a synchronous call, but the provider answers from its own CQRS read model (no peer aggregation, never partial). |
| `HTTP (kept)` | Deliberately kept synchronous (justification in section 9.3). |

Requirements that originate in the work order or assignment (US105, US106a, US115, US119, US204, US222) are identified as assignment requirements; bridge status indicates which consumer/provider sides are implemented.

---

## 2. Global conventions

### 2.1 Paths
- Every business endpoint is under **`/api`**, declared in the controller's class-level `@RequestMapping` (there is no context-path or global prefix).
- Resource names are used **exactly as in code**: `/api/routes`, `/api/scheduled-flights`, `/api/aircrafts` (sic), `/api/aircraft-models`, `/api/airports`, `/api/maintenance-templates`, `/api/maintenance-records`, `/api/maintenance-alerts`.
- Paths that are not business endpoints (no `/api`):
  - `POST /auth/dev-token`
  - `/actuator/health`, `/actuator/health/**`, `/actuator/info`
  - `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
  - `/h2-console/**`, `/error`
- Source: `FR/flightroutes/controllers/FlightRouteController.java`, `FR/flightroutes/controllers/ScheduledFlightController.java`, `COMMON/security/JwtSecurityAutoConfiguration.java`, `FR/flightroutes/clients/AircraftClient.java`, `FR/flightroutes/clients/AirportClient.java`

### 2.2 JSON, dates and units
- **Field names are camelCase**, exactly as the Java DTO properties (Jackson defaults, no naming strategy configured). Some names keep upper-case acronyms: `originIATA`, `destIATA`, `routeID`.
- **Dates and times use ISO-8601:**
  - date `"2026-11-02"` (`LocalDate`)
  - time `"08:00:00"` (`LocalTime`)
  - query parameters use `@DateTimeFormat(iso = DATE | TIME)`
- **Enums travel as upper-case strings**, not as JSON enums. Values in use:
  - aircraft status: `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`
  - airport status: `OPERATIONAL` (the only value Flight Routes checks)
  - route status: `ACTIVE`, `INACTIVE`
  - scheduled-flight status: `SCHEDULED` (future states: `DELAYED`, `IN_FLIGHT`, `COMPLETED`, `CANCELED`)
- **Units:**
  - distances in km (Haversine)
  - flight times in minutes
  - `maximumRange` / `minRange` are compared directly, so they use the same unit as the route distance: kilometres (Q9)
- **Unknown fields are ignored** by the Flight Routes clients (`@JsonIgnoreProperties(ignoreUnknown = true)`). Providers may add fields, including HATEOAS `_links`, but must not rename or remove the ones listed here.
- Source: `FR/flightroutes/dto/ScheduledFlightDTO.java`, `FR/flightroutes/dto/FlightRouteDTO.java`, `FR/flightroutes/controllers/ScheduledFlightController.java`, `FR/flightroutes/services/CreateScheduledFlightService.java` (aircraft status comment), `FR/flightroutes/clients/AircraftSnapshot.java`, `FR/flightroutes/clients/AirportSnapshot.java`, `FR/flightroutes/domain/RouteStatus.java`, `FR/flightroutes/domain/FlightStatus.java`, `FR/flightroutes/domain/RouteDistance.java`

### 2.3 Identifiers

| Identifier | Format in code | Notes |
|---|---|---|
| IATA code | 3 characters (`@Size(min=3,max=3)` on `CreateRouteDTO`). Flight Routes **upper-cases** it before calling Airports | No letters-only regex exists. Providers should accept and return upper case |
| Aircraft registration (`registrationNumber`) | Validated by Aircraft Management on creation (`^[A-Z0-9]{1,2}-[A-Z0-9]{2,5}$`, upper case), e.g. `CS-TUA`. Flight Routes sends it **as received** (`@NotBlank` only); Maintenance trims and upper-cases it | Lookups are exact and case-sensitive (Q10, section 8) |
| Aircraft model (`modelName`) | Free string, e.g. `A320`. Taken from the aircraft response and used as a path variable | |
| Route ID, scheduled-flight ID | UUID string (`@GeneratedValue(strategy = UUID)`) | Generated by the replica that stores the entity, so it is globally unique across replicas |

Path variables are expanded with URI templates, so they are URL-encoded (e.g. a space in a model name).

Source: `FR/flightroutes/dto/CreateRouteDTO.java`, `FR/flightroutes/dto/CreateScheduledFlightDTO.java`, `FR/flightroutes/clients/AirportClient.java`, `FR/flightroutes/domain/FlightRoute.java`, `FR/flightroutes/domain/ScheduledFlight.java`

### 2.4 Error body and status codes
Every service has a `GlobalExceptionHandler` (each skeleton already contains one), which returns:

```json
{ "error": "Aircraft not found: CS-XYZ" }
```

Exceptions to this shape:
- **Bean Validation failures** (`@Valid`) return a map from field to message, with no `error` key: `{"minRange": "Minimum range must be positive"}`.
- **401 for a missing or invalid JWT** has an **empty body** (`HttpStatusEntryPoint`). A **401 from `ServiceKeyFilter`** (missing or wrong `X-Service-Key`, section 2.8) does use `{"error": "..."}`.
- **403 (wrong role)** and **errors without a handler** (e.g. a missing required query parameter) use Spring Boot's default error body, not `{"error": ...}`.

Consumers should therefore decide on the **status code**, not on the body.

| Status | Meaning (exception → status in `GlobalExceptionHandler`) |
|---|---|
| 200 | OK |
| 201 | Created. `POST /api/scheduled-flights` also sets `Location`; `POST /api/routes` does not |
| 400 | `IllegalArgumentException`, validation failure, invalid `X-Peer-Hops` |
| 401 | No or invalid JWT; peer request without `X-Service-Key`; request to a service-only endpoint without `X-Service-Key` (section 2.8); wrong `X-Service-Key` |
| 403 | Authenticated, but the role is not allowed |
| 404 | `EntityNotFoundException` (local entity, or remote aircraft/airport not found) |
| 409 | `IllegalStateException` (business conflict) or `ObjectOptimisticLockingFailureException` (`@Version` clash; message `"The resource was updated by another user. Please refresh and try again."`) |
| 422 | `RouteRequirementsNotMetException` (Flight Routes only; not in the skeleton handlers) |
| 503 | `RemoteServiceUnavailableException`: no replica of a remote service answered. Message `"<Service> service is unavailable."` |
| 500 | Any exception without a handler, e.g. an unexpected 4xx from a remote service (see B1/B2) |

Source: `FR/exceptions/GlobalExceptionHandler.java`, `code/aircraftmanagement/src/main/java/isep/psoft/aisafe/exceptions/GlobalExceptionHandler.java` (same as Flight Routes, without the 422 handler; airports and maintenance likewise), `COMMON/security/JwtSecurityAutoConfiguration.java`, `COMMON/security/ServiceKeyFilter.java`, `COMMON/remote/RemoteServiceUnavailableException.java`

### 2.5 Headers, authentication and correlation ID

| Header | Direction | Set by | Checked by |
|---|---|---|---|
| `Authorization: Bearer <JWT>` | request | End user. On every inter-service and peer call it is **copied from the incoming request** (`OutboundHeaders.capture`, added by `InterServiceHeadersInterceptor`) | `JwtAuthenticationFilter`, then the service's `AuthorizationRules` |
| `X-Service-Key` | request | `OutboundHeaders` (value of `aisafe.security.service-key`) on every inter-service and peer call | `ServiceKeyFilter`: **mandatory on peer calls** (`X-Peer-Hops` present) **and on the service-only paths** of the provider (`aisafe.security.service-only-paths`, section 2.8); if sent on any other request it must match, otherwise 401 |
| `X-Peer-Hops` | request | `PeerClient` only (replica → replica of the same service), value `1` | `ServiceKeyFilter` (must be 1..`MAX_PEER_HOPS`=1, otherwise 400); `PeerClient.isPeerRequest()` makes the receiver answer with local data only |
| `X-Correlation-Id` | request and response | `CorrelationIdFilter` (reuses the incoming id if it matches `[A-Za-z0-9._-]{1,64}`, otherwise a new UUID), echoed in the response and propagated by `OutboundHeaders` | Written to every log line (MDC) and to the audit log |
| `X-Caller-Instance` | request | `OutboundHeaders` (`aisafe.instance-id`) | Informational only |
| `X-Partial-Response: true` | response | `PeerClient.markPartial` when at least one peer did not answer | Consumers (optional) |
| `X-Unavailable-Peers: <n>` | response | Same as above | Consumers (optional) |

**Authentication between services:**
1. The caller's **JWT is propagated**, so the provider authorises the **end user's roles**, not the calling service. When you implement a provider endpoint, allow the roles of the users whose use cases trigger the call (listed in each bridge).
2. All services share `jwt.secret`. The roles go in the `roles` claim as `ROLE_<NAME>`. The roles that exist are `ADMIN`, `BACKOFFICE_OPERATOR`, `ATCC`, `MAINTENANCE_TECHNICIAN` and `MAINTENANCE_SUPERVISOR`.
3. `X-Service-Key` is **sent automatically on every inter-service and peer call** (the `common` `RestClient` adds it; service code never sets it). It is **required** on peer calls and on the endpoints a provider declares as service-only (section 2.8); on other endpoints a call without it is accepted if the JWT is valid (Q11, decided).

Source: `COMMON/http/AisafeHeaders.java`, `COMMON/http/OutboundHeaders.java`, `COMMON/http/InterServiceHeadersInterceptor.java`, `COMMON/security/ServiceKeyFilter.java`, `COMMON/observability/CorrelationIdFilter.java`, `COMMON/peers/PeerClient.java`, `COMMON/security/JwtTokenProvider.java`, `COMMON/security/JwtAuthenticationFilter.java`, `COMMON/security/DevTokenController.java`

### 2.6 Pagination
Every paginated endpoint paginates **in memory, after merging every replica's data**, with `page` (0-based) and `size`. There are three page formats (Q8):

| Service | Endpoints | Format | Limits |
|---|---|---|---|
| Flight Routes | `GET /api/scheduled-flights?aircraft=…` (US213) | HAL `PagedModel` (Spring HATEOAS): `_embedded` (key derived from the DTO class name; no `@Relation` declared), `_links`, `page {size, totalElements, totalPages, number}` | Spring defaults: size 20, maximum 2000 |
| Flight Routes | `GET /api/scheduled-flights/operational-hours` (B4a, US206) | Spring Data `Page` serialised **directly** (`spring.data.web.pageable.serialization-mode` default `direct` in Spring Boot 4.0.6): `content`, `totalElements`, `totalPages`, `size`, `number`, `numberOfElements`, `first`, `last`, `empty`, `sort`, `pageable` | Spring defaults: size 20, maximum 2000 |
| Aircraft Management | `GET /api/aircrafts` (US104), `GET /api/aircrafts/operational-hours` (US206) | `PageDTO`: `{ "content": [...], "page", "size", "totalElements", "totalPages" }` | size 1–100 (default 20), otherwise 400 |
| Maintenance | `GET /api/maintenance-records` (US218), `GET /api/maintenance-records/aircraft/{registration}` (US116), `GET /api/maintenance-records/ongoing` (US219) | `PageDTO`, same fields as Aircraft Management | size 1–100 (default 20), otherwise 400 |

Things to know:
- In Flight Routes, `sort` is **ignored** by `Pages.of`. Operational hours are always sorted by `registrationNumber`.
- A **peer** request to `GET /api/aircrafts` gets the plain local list (no page), which the receiving replica merges and pages. Maintenance uses separate non-paginated endpoints for peers (`GET /api/maintenance-records/search`).
- No other endpoint paginates; collections are plain JSON arrays or objects.

Source: `FR/flightroutes/controllers/ScheduledFlightController.java`, `FR/flightroutes/services/Pages.java`, `FR/flightroutes/services/CalculateOperationalHoursService.java`, `code/aircraftmanagement/.../controllers/AircraftController.java`, `AircraftStatisticsController.java`, `dto/PageDTO.java`, `code/maintenance/.../controllers/MaintenanceRecordController.java`, `dto/PageDTO.java`, `spring-boot-data-commons-4.0.6.jar` configuration metadata (default `direct`)

### 2.7 Configuration properties
| Property | Purpose | Defaults (in `AisafeProperties`, repeated in each `application.properties`) |
|---|---|---|
| `services.<name>.urls` | Base URLs of **all replicas** of another service, comma-separated. Names in use: `services.aircraft.urls`, `services.airports.urls`, `services.maintenance.urls`, `services.flightroutes.urls` | — |
| `aisafe.peers.urls` | Base URLs of the **other** replicas of the same service (excluding itself). One per instance profile | — |
| `aisafe.instance-id` | Replica name in logs and audit | `unknown` → `spring.application.name:server.port` |
| `aisafe.security.service-key` | Shared inter-service secret (`AISAFE_SERVICE_KEY`) | empty |
| `aisafe.security.service-only-paths` | Paths (Ant patterns) that only other AISafe services may call: without a valid `X-Service-Key` → 401 (section 2.8). Set only by Flight Routes | empty |
| `jwt.secret` | Shared JWT signing key (`JWT_SECRET`) | — |
| `aisafe.http.connect-timeout` | Connect timeout of every inter-service call | `1s` |
| `aisafe.http.read-timeout` | Read timeout of every inter-service call | `3s` |
| `aisafe.http.max-retries` | Extra attempts after a transient failure | `2` |
| `aisafe.http.initial-backoff` | Wait before the first retry; doubles on each attempt | `100ms` |
| `aisafe.http.aggregation-timeout` | Maximum wait for all peers in an aggregation | `5s` |
| `aisafe.circuit-breaker.failure-threshold` | Consecutive failures before the circuit (per base URL) opens | `3` |
| `aisafe.circuit-breaker.open-duration` | Time OPEN before HALF_OPEN | `10s` |

**Transient failures** are timeouts, refused connections (`ResourceAccessException`), any 5xx (`HttpServerErrorException`) and an open circuit (`CircuitOpenException`). They are retried, count towards the circuit breaker and trigger failover to the next replica. **A 4xx is not transient**: the target is alive, so the 4xx counts as a success for the circuit breaker and is passed to the caller as-is, with no retry and no failover.

Source: `COMMON/config/AisafeProperties.java`, `COMMON/resilience/ResilientExecutor.java`, `COMMON/resilience/CircuitBreaker.java`, `COMMON/remote/ReplicatedServiceClient.java`, `FR/flightroutes/clients/RestClientConfig.java`, `code/*/src/main/resources/application.properties`

### 2.8 Service-only endpoints
Some provider endpoints return raw data that only another service uses (counts, minutes, compatible routes). They are listed in the provider's `aisafe.security.service-only-paths`, and `ServiceKeyFilter` rejects any request to them that has **no** `X-Service-Key`, **even with a valid JWT**:

```json
{"error":"This endpoint is only available to AISafe services."}
```

A wrong key gets `401 {"error":"Invalid service key."}`. With a valid key the request continues to the JWT and role checks as usual (the key does not replace the JWT). Paths are matched after removing the context path and decoding the segments, so an encoded variant (`/api/routes/%63ompatible`) is also rejected.

Configured in Flight Routes (`code/flightroutes/src/main/resources/application.properties`); no other service declares service-only paths:

| Path | Bridge | Called by |
|---|---|---|
| `/api/routes/statistics/count-by-airport` | B5 | Airports |
| `/api/routes/compatible` | B6 | Aircraft Management |
| `/api/scheduled-flights/statistics/minutes-by-aircraft` | B4b | Aircraft Management |
| `/api/scheduled-flights/operational-hours` | B4a | no consumer (Flight Routes replicas only) |
| `/api/scheduled-flights/statistics/usage-by-route` | — | Flight Routes replicas (US214 aggregation) |

Consumers need no code for this: the key is added by `common` to every outgoing call.

Source: `COMMON/security/ServiceKeyFilter.java`, `COMMON/config/AisafeProperties.java` (`Security.serviceOnlyPaths`), `COMMON/security/JwtSecurityAutoConfiguration.java`, `code/flightroutes/src/test/java/isep/psoft/aisafe/distributed/ServiceOnlyEndpointsSecurityTest.java`, `code/common/src/test/java/isep/sidis/common/security/ServiceKeyFilterTest.java`

---

## 3. Bridge catalogue

| ID | Consumer → Provider | Purpose (US) | Endpoint | Status | Assignment 2 (section 9) |
|---|---|---|---|---|---|
| B1a | Flight Routes → Aircraft Management | Aircraft status, capacity and model (US212, US213) | `GET /api/aircrafts/{registrationNumber}` | `IMPLEMENTED` | `HTTP (waiting for Aircraft Management events)` |
| B1b | Flight Routes → Aircraft Management | Maximum range of the aircraft model (US212) | `GET /api/aircraft-models/{modelName}` | `IMPLEMENTED` | `HTTP (waiting for Aircraft Management events)` |
| B2 | Flight Routes → Airports | Coordinates, existence, operational status (US110, US209, US212, US216) | `GET /api/airports/{iataCode}` | `IMPLEMENTED` | `HTTP (waiting for Airports events)` |
| B3a | Maintenance → Aircraft Management | Aircraft exists and its model, for template applicability (US115, US116) | reuse B1a (`GET /api/aircrafts/{registrationNumber}`) | `IMPLEMENTED` | `HTTP (waiting for Aircraft Management events)` |
| B3b | Maintenance → Aircraft Management | Set the status to `UNDER_MAINTENANCE` / back to `AVAILABLE` (US115, US119) | `PATCH /api/aircrafts/{registrationNumber}/status` (US105) | `IMPLEMENTED` | `HTTP (kept)`: a command |
| B3c | Maintenance → Aircraft Management | Applicable models of a template exist (US115 templates) | reuse B1b (`GET /api/aircraft-models/{modelName}`) | `IMPLEMENTED` | `HTTP (waiting for Aircraft Management events)` |
| B3d | Maintenance → Aircraft Management | Fleet and flight hours per aircraft (US222) | `GET /api/aircrafts?page=&size=` (US104), `GET /api/aircrafts/operational-hours?page=&size=` (US206) | `IMPLEMENTED` | `HTTP (waiting for Aircraft Management events)` |
| B4a | (none) → Flight Routes | Paged operational hours per aircraft | `GET /api/scheduled-flights/operational-hours` (service-only) | `IMPLEMENTED (provider only)`: no service calls it; Aircraft Management computes US206 from B4b | `HTTP (provider uses read model)` |
| B4b | Aircraft Management → Flight Routes | Flight minutes per aircraft: US204 (top models) and US206 (operational hours) | `GET /api/scheduled-flights/statistics/minutes-by-aircraft` (service-only) | `IMPLEMENTED` | `HTTP (provider uses read model)`; `EVENT-BASED` possible now |
| B5 | Airports → Flight Routes | Number of routes per airport (US210) | `GET /api/routes/statistics/count-by-airport` (service-only) | `IMPLEMENTED` | `HTTP (provider uses read model)`; `EVENT-BASED` possible now |
| B6 | Aircraft Management → Flight Routes | Active routes compatible with an aircraft (US203) | `GET /api/routes/compatible?range=&capacity=` (service-only) | `IMPLEMENTED` | `HTTP (provider uses read model)`; `EVENT-BASED` possible now |

**Design rule:** Aircraft Management's aircraft `status` is the **single source of truth for availability**. Maintenance changes it through B3b. **Flight Routes never calls Maintenance**; it only reads the status through B1a.

---

## 4. Bridges

### B1: Flight Routes → Aircraft Management (`IMPLEMENTED`)

**Consumer:** `AircraftClient` (Flight Routes). **Provider:** Aircraft Management — `AircraftController.getByRegistration` (B1a) and `AircraftModelController.getByName` (B1b), which look the entity up in any replica (`AircraftReplicaQueries`). The provider returns more fields than listed below (e.g. `manufacturingDate`, `manufacturer`); Flight Routes ignores them.

**Use cases that trigger the calls:**

| Use case | Service | Call | What it does with the result |
|---|---|---|---|
| US212 create scheduled flight | `CreateScheduledFlightService` | `getAircraft` → B1a then B1b | `registrationNumber`, `seatingCapacity`, `maximumRange`, `status` |
| US213 scheduled flights of an aircraft | `ViewScheduledFlightsByAircraftService` | `exists` → B1a only | existence only |

#### B1a: `GET /api/aircrafts/{registrationNumber}`
- **Request:** path variable `registrationNumber`, as typed by the user (not normalised). No body or query parameters.
- **Response 200**, a JSON object. Fields Flight Routes reads (all others, including `_links`, are ignored):

| Field | JSON type (Java) | Required | Description |
|---|---|---|---|
| `registrationNumber` | string (`String`) | **yes** | Stored in the new scheduled flight and used for the overlap check |
| `modelName` | string (`String`) | **yes** | Used as the path variable of B1b |
| `seatingCapacity` | integer (`Integer`) | **yes** | Compared with the route's `minCapacity`. Null causes a 500 (unboxed into `int`) |
| `status` | string (`String`) | **yes** | One of `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`. **Only `"AVAILABLE"`** (exact, case-sensitive) lets a flight be scheduled |

```json
{ "registrationNumber": "CS-TUA", "modelName": "A320", "seatingCapacity": 180, "status": "AVAILABLE" }
```

#### B1b: `GET /api/aircraft-models/{modelName}`
- **Request:** path variable `modelName` (value from B1a).
- **Response 200:**

| Field | JSON type (Java) | Required | Description |
|---|---|---|---|
| `modelName` | string (`String`) | no (deserialised, not used) | Model name |
| `maximumRange` | number (`Double`) | **yes** | Compared with the route's `minRange` (same unit as route distances, km). Null causes a 500 (unboxed into `double`) |

```json
{ "modelName": "A320", "maximumRange": 6100 }
```

#### Status codes and how Flight Routes handles them (B1a and B1b)

| Provider answers | Flight Routes does | Flight Routes' client gets |
|---|---|---|
| 200 | uses the fields above | — |
| 404 on B1a | `EntityNotFoundException("Aircraft not found: <reg>")`; `exists()` returns `false` | **404** (US212, US213) |
| 404 on B1b | `EntityNotFoundException("Aircraft model not found: <model>")` | **404** (US212) |
| 401 / 403 | `ResponseStatusException(<same status>, "Aircraft Management rejected the request.")` | **same 401/403**, `{"error": "Aircraft Management rejected the request."}` |
| other 4xx (e.g. 400) | not handled: the `HttpClientErrorException` propagates | **500**. Providers should return only 200/404/401/403 here |
| 5xx, timeout, connection refused | retried (`max-retries`, exponential backoff), then the next replica (round-robin start, failover) | **503** `{"error": "Aircraft Management service is unavailable."}` if no replica succeeds |
| circuit open on a replica | that replica is skipped (next replica) | 503 if all are open or failing |

US212 then applies its own rules to the data:
- range or capacity too low → **422**
- aircraft not `AVAILABLE` → **409** `"Aircraft <reg> is not available (status: <status>)."`

**Required role on the provider:** the JWT forwarded is the end user's. Flight Routes' rules require **`ATCC`** for `POST /api/scheduled-flights` (US212) and `GET /api/scheduled-flights/**` (US213). Aircraft Management allows `ATCC`, `BACKOFFICE_OPERATOR`, `MAINTENANCE_TECHNICIAN` and `MAINTENANCE_SUPERVISOR` on `GET /api/aircrafts/**` and `GET /api/aircraft-models/**`.

**Replica behaviour:**
- **Consumer:** load balancing across all replicas listed in `services.aircraft.urls`, with failover (`ReplicatedServiceClient`).
- **Provider:** the replica that receives the GET finds the aircraft (or model) in any replica: local first, then `PeerClient.findFirst` (section 5).

**Source:** `FR/flightroutes/clients/AircraftClient.java`, `FR/flightroutes/clients/AircraftSnapshot.java`, `FR/flightroutes/clients/RestClientConfig.java`, `FR/flightroutes/services/CreateScheduledFlightService.java`, `FR/flightroutes/services/ViewScheduledFlightsByAircraftService.java`, `FR/flightroutes/domain/RouteRequirements.java`, `FR/exceptions/GlobalExceptionHandler.java`, `FR/infrastructure/security/FlightRoutesAuthorizationRules.java`, `COMMON/remote/ReplicatedServiceClient.java`, `COMMON/resilience/ResilientExecutor.java`; example values from the stub in `code/flightroutes/src/test/java/isep/psoft/aisafe/distributed/ReplicaCollaborationTest.java`

---

### B2: Flight Routes → Airports (`IMPLEMENTED`)

**Consumer:** `AirportClient` (Flight Routes). **Provider:** Airports — `AirportController.getByIata` (US107), any replica, case-insensitive IATA.

**Use cases that trigger the call:**

| Use case | Service | Call | What it does with the result |
|---|---|---|---|
| US110 create route | `CreateFlightRouteService` | `getAirport` (origin and destination) | `iataCode`, `latitude`, `longitude` (distance) |
| US209 routes by airport | `ViewRoutesByAirportService` | `exists` | existence |
| US212 create scheduled flight | `CreateScheduledFlightService` | `getAirport` (origin and destination) | `status` |
| US216 alternative routes | `SearchAlternativeRoutesService` | `exists` (origin and destination) | existence |

#### `GET /api/airports/{iataCode}`
- **Request:** path variable `iataCode`, **upper-cased** by the consumer. No body or query parameters.
- **Response 200:**

| Field | JSON type (Java) | Required | Description |
|---|---|---|---|
| `iataCode` | string (`String`) | **yes** | Stored as the route's origin/destination (US110) |
| `latitude` | number (`Double`) | **yes** | Decimal degrees. Null causes a 500 (unboxed into `double`) |
| `longitude` | number (`Double`) | **yes** | Decimal degrees. Null causes a 500 |
| `status` | string (`String`) | **yes** | **Only `"OPERATIONAL"`** (exact) counts as operational. Any other value blocks US212 |

```json
{ "iataCode": "OPO", "latitude": 41.2481, "longitude": -8.6814, "status": "OPERATIONAL" }
```

**Certifications (US106a):** Flight Routes does **not** read any aircraft-model certification of the airport. No field is consumed and no check exists in US212. Airports returns `certifiedModels` in the response, but nothing depends on it (Q3; known limitation, section 8).

#### Status codes and how Flight Routes handles them

| Provider answers | Flight Routes does | Flight Routes' client gets |
|---|---|---|
| 200 | uses the fields above | — |
| 404 | `EntityNotFoundException("Airport not found: <IATA>")`; `exists()` returns `false` | US110: **400** `"Origin airport not found: <IATA>"` / `"Destination airport not found: <IATA>"`. US209, US212, US216: **404** |
| 400 | `IllegalArgumentException("Invalid IATA code: <IATA>")` | **400** |
| 401 / 403 | `ResponseStatusException(<same status>, "Airports rejected the request.")` | **same 401/403** |
| other 4xx | not handled | **500** |
| 5xx, timeout, connection refused, circuit open | retry, then failover to the next replica | **503** `{"error": "Airports service is unavailable."}` |

US212 then answers **409** `"Origin airport <IATA> is not operational."` (or Destination) when the status is not `OPERATIONAL`.

**Required role on the provider:** **`ATCC`**. US110, US212 and US216 are `ATCC`-only in Flight Routes, and so is US209 (`GET /api/routes/**`).

**Replica behaviour:**
- **Consumer:** load balancing and failover over `services.airports.urls`.
- **Provider:** must find the airport in any replica (`PeerClient.findFirst`), as in B1.

**Source:** `FR/flightroutes/clients/AirportClient.java`, `FR/flightroutes/clients/AirportSnapshot.java`, `FR/flightroutes/services/CreateFlightRouteService.java`, `FR/flightroutes/services/ViewRoutesByAirportService.java`, `FR/flightroutes/services/CreateScheduledFlightService.java`, `FR/flightroutes/services/SearchAlternativeRoutesService.java`, `FR/flightroutes/services/DistanceCalculatorService.java`, `FR/infrastructure/security/FlightRoutesAuthorizationRules.java`; example values from `ReplicaCollaborationTest.java`

---

### B3: Maintenance → Aircraft Management (`IMPLEMENTED`)

**Consumer:** Maintenance — `AircraftClient` (`code/maintenance/.../maintenance/clients/AircraftClient.java`), translated into `AircraftSnapshot`. **Provider:** Aircraft Management (B3a reuses B1a, B3b is US105, B3c reuses B1b, B3d reuses US104 and US206).

**Use cases that trigger the calls:**

| Use case | Maintenance service | Call | What it does with the result |
|---|---|---|---|
| US115 create template | `CreateMaintenanceTemplateService` | B3c per applicable model | existence (404 → template rejected with 400) |
| US115 create record | `CreateMaintenanceRecordService` | B3a, then B3b `UNDER_MAINTENANCE` | `modelName` (template applicability, model snapshot), `status` (previous status, for compensation) |
| US116 records of an aircraft | `ViewMaintenanceRecordsService` | B3a (only on the replica that received the client request) | existence (404 → 404, not an empty history) |
| US119 complete record | `CompleteMaintenanceRecordService` → `AircraftAvailabilityService` (replica that received the client request) | B3b `AVAILABLE` (only if no other record of the aircraft is ongoing in any replica and every replica answered) | — (failure keeps the aircraft `UNDER_MAINTENANCE`; header `X-Aircraft-Released: false`) |
| US222 alerts | `MaintenanceAlertService` | B3d (all pages) | fleet (`registrationNumber`, `modelName`, `manufacturingDate`, `status`) and `totalHours` per aircraft |

#### B3a: `GET /api/aircrafts/{registrationNumber}` (reuses B1a)
- Same request and response as **B1a**. Maintenance reads `registrationNumber`, `modelName` and `status` (and `manufacturingDate` in B3d). The registration is trimmed and upper-cased by Maintenance before the call.
- **404** → `EntityNotFoundException("Aircraft not found: <reg>")` → Maintenance answers **404** and creates nothing (group decision: 404, consistent with Flight Routes).
- Roles allowed by the provider (already in `AircraftManagementAuthorizationRules`): `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR` (plus `ATCC`, `BACKOFFICE_OPERATOR`).

#### B3b: `PATCH /api/aircrafts/{registrationNumber}/status` (US105)
- **Request body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `status` | string | yes | New status: `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE` |

```json
{ "status": "UNDER_MAINTENANCE" }
```

- **Usage:**
  - US115 (record starts): `{"status": "UNDER_MAINTENANCE"}`. If the aircraft is already `UNDER_MAINTENANCE` (another record in progress) the provider accepts it as a no-op.
  - US119 (last ongoing record completed): `{"status": "AVAILABLE"}`. **Not `"ACTIVE"`**: Flight Routes only schedules aircraft whose status is exactly `AVAILABLE` (Q1, decided).
  - Compensation (US115): the previous status, when the local transaction rolls back after B3b succeeded.
- **Response 200:** the updated aircraft, with the same schema as B1a.
- **Status codes and how Maintenance handles them:**

| Status | When | Maintenance does | Maintenance's client gets |
|---|---|---|---|
| 200 | updated (or already in that status) | commits the record | 201 (US115) / 200 (US119) |
| 400 | invalid status value | not expected (Maintenance only sends fixed values) → rollback | 400 |
| 401/403 | role not allowed | `ResponseStatusException`, rollback | same 401/403 |
| 404 | aircraft not found | `EntityNotFoundException`, rollback | 404 |
| 409 | aircraft `IN_FLIGHT` cannot go `UNDER_MAINTENANCE` (Q7, decided by Aircraft Management) or `@Version` clash | `ResponseStatusException(409)`, rollback | 409 |
| 5xx, timeout, connection refused, circuit open | retry, then failover to the next replica | `RemoteServiceUnavailableException`, rollback | **503** `{"error": "Aircraft Management service is unavailable."}` |

- **Ordering (decided, Q7):** US115 — Maintenance writes and **flushes** its record first (local validation, constraints and `@Version` already checked), then calls B3b, then commits. B3b failing → the local transaction rolls back (nothing changed anywhere). Commit failing after B3b → a `TransactionSynchronization` sends the **compensating PATCH**. US119 — the record is completed first; the release is a separate step whose failure leaves the aircraft `UNDER_MAINTENANCE` (safe side) without failing the completion. The server-side `@Version` of the aircraft is enough; no `If-Match` is sent.
- **Retries:** setting a status to a fixed value is idempotent, so the retries of `ReplicatedServiceClient` are safe.
- **Replica behaviour (provider):** the write runs on the replica that owns the aircraft (`UpdateAircraftStatusService` → `PeerClient.forwardToOwner`).

#### B3c: `GET /api/aircraft-models/{modelName}` (reuses B1b)
- Called once per applicable model of a new template. Only the status code matters: **200** → the model exists; **404** → template rejected with **400** `"Unknown aircraft model: <model>"`; other errors as in B3b (401/403 propagated, 503).
- Role: `MAINTENANCE_TECHNICIAN` (allowed by `GET /api/aircraft-models/**`).

#### B3d: fleet and flight hours (US222)
- `GET /api/aircrafts?page=n&size=100` (US104, no filters) — read page by page until `page = totalPages - 1`. Fields used from each `content` element: `registrationNumber`, `modelName`, `manufacturingDate`, `status`.
- `GET /api/aircrafts/operational-hours?page=n&size=100` (US206) — fields used: `registrationNumber`, `totalHours`. Aircraft without flights have `0`.
- Page format (both): `{ "content": [...], "page": 0, "size": 100, "totalElements": 6, "totalPages": 1 }` (`PageDTO` of Aircraft Management).
- `X-Partial-Response: true` on either response → Maintenance flags its own response as partial.
- `operational-hours` depends on Flight Routes (B4). If it fails (`503`, or any error other than a 404 of the fleet), Maintenance still returns the calendar-day alerts, flagged `X-Partial-Response: true`.
- Role: **`ATCC`** (the US222 actor; `operational-hours` is `ATCC`-only).

**Source:** `code/maintenance/.../maintenance/clients/AircraftClient.java`, `code/aircraftmanagement/.../controllers/AircraftController.java`, `AircraftStatisticsController.java`, `AircraftModelController.java`, `UpdateAircraftStatusService.java`, `domain/Aircraft.java` (`changeStatus`), `dto/PageDTO.java`, `infrastructure/security/AircraftManagementAuthorizationRules.java`, `FR/flightroutes/clients/AircraftSnapshot.java` (`isAvailable` checks `"AVAILABLE"`), `COMMON/remote/ReplicatedServiceClient.java`

---

### B4: Aircraft Management → Flight Routes (usage statistics)

**Consumer:** Aircraft Management — `FlightRoutesClient.minutesByAircraft` (B4b only). **Provider:** Flight Routes.

**Use cases:**
- US206: total operational hours per aircraft — `GET /api/aircrafts/operational-hours` (Aircraft Management, `CalculateOperationalHoursService`).
- US204: top most utilised aircraft models — `GET /api/aircraft-models/top-utilized?limit=` (default 5, 1–20; Aircraft Management, `TopUtilizedModelsService`).

Flight Routes only knows **registrations**, not models. Aircraft Management therefore maps registration → model itself, using its own fleet from all its replicas.

#### B4a: `GET /api/scheduled-flights/operational-hours`, `IMPLEMENTED (provider only)`
No service calls this endpoint: Aircraft Management computes US206 from B4b and its own fleet. It is service-only (section 2.8), so a user calling it directly gets 401.
- **Request:** optional `page` (0-based) and `size` (default 20, maximum 2000). `sort` is ignored.
- **Response 200:** Spring Data `Page` (format in section 2.6). Each element of `content`:

| Field | Type | Required | Description |
|---|---|---|---|
| `registrationNumber` | string | yes | Aircraft registration |
| `totalMinutes` | integer (`Long`) | yes | Sum of `estimatedFlightTime` of all its scheduled flights, in **all replicas and all flight statuses** |
| `totalHours` | number (`Double`) | yes | `totalMinutes / 60` rounded to 2 decimals (computed getter) |

```json
{ "content": [ { "registrationNumber": "CS-TUA", "totalMinutes": 55, "totalHours": 0.92 } ],
  "totalElements": 1, "totalPages": 1, "size": 20, "number": 0, "first": true, "last": true,
  "numberOfElements": 1, "empty": false, "sort": { "...": "..." }, "pageable": { "...": "..." } }
```

- Only aircraft **with at least one flight** appear.
- Content is sorted by `registrationNumber`.

#### B4b: `GET /api/scheduled-flights/statistics/minutes-by-aircraft` (US204, US206), `IMPLEMENTED`
- **Request:** no parameters.
- **Response 200:** a JSON object whose keys are registrations and whose values are minutes (`Map<String, Long>`). The minutes are the same as `totalMinutes` in B4a, but the whole result comes at once without pagination, which is simpler for a top-5 ranking.

```json
{ "CS-TUA": 55, "CS-TUB": 140 }
```

- **US204 recipe (consumer, as implemented):** call B4b, map each registration of the fleet to its `modelName`, sum the minutes per model, drop models with 0 minutes, sort by minutes descending (ties by model name) and take `limit`. Each row: `modelName`, `totalMinutes`, `totalHours`, `aircraftCount`.
- **US206 recipe (consumer, as implemented):** every aircraft of the fleet (sorted by registration) with the minutes from B4b, **0** when absent; paginated with Aircraft Management's `PageDTO`. Each row: `registrationNumber`, `modelName`, `totalMinutes`, `totalHours`.
- "Most utilised" means **flight time** (Q5).

#### Status codes, role and replicas (B4a and B4b)

| Status | When |
|---|---|
| 200 | always, when authorised |
| 401 | missing or invalid JWT, or **no `X-Service-Key`** (service-only, section 2.8) |
| 403 | the user's role is not allowed (below) |

- **Roles (Q4):** B4b (`minutes-by-aircraft`) allows **`ATCC` and `BACKOFFICE_OPERATOR`**, so US204 works for both roles that Aircraft Management allows on `top-utilized`. B4a and the rest of `GET /api/scheduled-flights/**` are `ATCC` only, which matches US206 (`GET /api/aircrafts/operational-hours` is `ATCC` only in Aircraft Management).
- **Replica behaviour (Assignment 2):** the receiving Flight Routes replica answers from its own flight read model (`REPLICATED_SCHEDULED_FLIGHT`), which holds the flights of every replica. No peer is called, so the answer is never partial and Flight Routes no longer sends `X-Partial-Response` here; it may be slightly stale (eventual consistency, a flight created on another replica a few milliseconds ago). Aircraft Management still reads the header (`.retrieve().toEntity(...)`) and simply never finds it.
- **Consumer failure handling:** the same as B1 (retry, then failover, then 503). A 401/403 from Flight Routes becomes the same status with `{"error": "Flight Routes rejected the request."}`.

**Source:** `code/aircraftmanagement/.../clients/FlightRoutesClient.java`, `services/CalculateOperationalHoursService.java`, `services/TopUtilizedModelsService.java`, `FR/flightroutes/controllers/ScheduledFlightController.java`, `FR/flightroutes/query/CalculateOperationalHoursService.java`, `FR/flightroutes/query/ScheduledFlightStatisticsService.java`, `FR/flightroutes/query/ReplicatedScheduledFlightRepository.java` (`sumMinutesByAircraft`), `FR/flightroutes/dto/AircraftOperationalHoursDTO.java`, `FR/flightroutes/query/Pages.java`, `FR/infrastructure/security/FlightRoutesAuthorizationRules.java`, `ReplicaCollaborationTest.java` (operational hours of flights owned by another replica)

---

### B5: Airports → Flight Routes (route counts per airport), `IMPLEMENTED`

**Consumer:** Airports — `FlightRoutesClient`, used by `AirportStatisticsService`. **Provider:** Flight Routes. **Use case:** US210, busiest airports by number of routes.

**Who owns which endpoint:**
- **Airports owns the user-facing endpoint** `GET /api/airports/statistics/busiest` (US210). Implemented by `AirportController.busiestAirports` (`?limit=`, 1–100, default 5).
- **Flight Routes only provides the raw counts.** Ranking, the top-N cut and the airport details are Airports' job.

#### `GET /api/routes/statistics/count-by-airport`
- **Request:** no parameters.
- **Response 200:** a JSON object whose keys are IATA codes and whose values are numbers of routes (`Map<String, Long>`):

```json
{ "OPO": 2, "LIS": 1, "MAD": 1 }
```

- A route counts **once for its origin and once for its destination**.
- **All routes are counted, `ACTIVE` and `INACTIVE` alike** (no status filter in `countByOrigin`/`countByDestination`). See Q6.
- Airports with no routes are **absent**; the consumer assumes 0.
- Counts from every replica are summed (each route lives in exactly one replica).

#### Status codes, role and replicas

| Status | When |
|---|---|
| 200 | authorised |
| 401 | missing or invalid JWT, or **no `X-Service-Key`** (service-only, section 2.8) |
| 403 | the role is not `BACKOFFICE_OPERATOR` |

- **Required role:** **`BACKOFFICE_OPERATOR`** (explicit rule), the same role Airports requires on `GET /api/airports/statistics/**`.
- **Replica behaviour (Assignment 2):** answered from the route read model of the receiving replica (`REPLICATED_ROUTE`, routes of every replica), as in B4: never partial, possibly slightly stale.
- **Consumer failure handling:** retry, failover, then 503; a 401/403 from Flight Routes becomes the same status with `{"error": "Flight Routes rejected the request."}`.

**Source:** `FR/flightroutes/controllers/FlightRouteController.java`, `FR/flightroutes/query/CountRoutesByAirportService.java`, `FR/flightroutes/query/ReplicatedRouteRepository.java`, `FR/infrastructure/security/FlightRoutesAuthorizationRules.java` (rule plus the `US210 — GET /api/airports/statistics/busiest (Airports)` comment)

---

### B6: Aircraft Management → Flight Routes (compatible routes), `IMPLEMENTED`

This bridge was not in the work order; it is included because it exists in code and in [Containers.md](Containers.md).

**Consumer:** Aircraft Management — `FindCompatibleRoutesService` behind `GET /api/aircrafts/{registrationNumber}/compatible-routes` (`ATCC` only). It finds the aircraft and its model in any of its replicas (404 if either is missing), calls B6 with the model's `maximumRange` and the aircraft's `seatingCapacity`, and passes `X-Partial-Response` on. **Provider:** Flight Routes. **Use case:** US203, routes an aircraft can operate.

#### `GET /api/routes/compatible?range={Double}&capacity={Integer}`
- **Request:** both query parameters are **required**: the aircraft's model `maximumRange` and its `seatingCapacity`. A missing parameter returns a 400 with Spring's default body.
- **Response 200:** a JSON array of `FlightRouteDTO`. It contains only **`ACTIVE`** routes whose `minRange <= range` and `minCapacity <= capacity`, from all replicas.

| Field | Type | Description |
|---|---|---|
| `id` | string (UUID) | Route ID |
| `originIATA`, `destIATA` | string | Airports |
| `distance` | number | km |
| `minRange` | number | Same unit as `maximumRange` |
| `minCapacity` | integer | seats |
| `estimatedFlightTime` | integer | minutes |
| `status` | string | `ACTIVE` |
| `usageCount` | integer or null | null here (only filled in by US214) |

- **Roles:** `ATCC`, `MAINTENANCE_SUPERVISOR` or `MAINTENANCE_TECHNICIAN` on the provider; in practice only `ATCC`, because the consumer endpoint is `ATCC` only.
- **Service-only:** without `X-Service-Key` → 401 (section 2.8).
- **Replica behaviour (Assignment 2):** answered from the route read model of the receiving replica, as in B4: never partial, possibly slightly stale.

**Source:** `FR/flightroutes/controllers/FlightRouteController.java`, `FR/flightroutes/query/SearchFlightRoutesService.java`, `FR/flightroutes/query/ReplicatedRouteRepository.java` (`findCompatibleRoutes`), `FR/flightroutes/dto/FlightRouteDTO.java`, `FR/infrastructure/security/FlightRoutesAuthorizationRules.java`

---

## 5. Intra-service peer protocol (replicas of the same service)

Every service runs 3 replicas, and **each replica has its own database**. Data is partitioned by the replica that received the write. Every service must behave as below, **reusing `common`**; do not write your own HTTP code.

> **Assignment 2.** This table is the Assignment 1 protocol. Aircraft Management, Airports and Maintenance still use all of it. **Flight Routes keeps only the write rows** (create on the receiving replica, forward writes to the owner): its reads come from CQRS read models fed by events, so it no longer uses `findFirst`, `collect`/`collectList`, `isPeerRequest` or `X-Partial-Response` ([Flight Routes L3 §6](../3-components/flightroutes/FlightRoutes-Components.md#6-cqrs-flow-write--event--projection-on-every-replica)). Its uniqueness check (US110) runs on its read model.

The auto-configuration provides The auto-configuration provides `PeerClient`, `ReplicatedServiceClientFactory`, `ResilientExecutor`, the filters and the security chain, so you only need to inject the beans.

| Situation | What to do | `common` API | Reference implementation |
|---|---|---|---|
| **GET by ID** | Look locally; if not found, ask the peers in parallel and use the first 200 (a 404 from a peer means "not mine"). Throw `EntityNotFoundException` if no replica has it | `peerClient.findFirst(uri, Type.class)` | `AirportReplicaQueries`, `AircraftReplicaQueries` |
| **GET collection / search** | Local results, plus the same GET on every peer, merged **by ID** (no duplicates) | `peerClient.collectList(uri, new ParameterizedTypeReference<List<T>>(){})` or `collect(...)` | `AirportReplicaQueries`, `MaintenanceReplicaQueries` |
| **Counts / sums** | Local partial values plus the peers' partial values, summed (valid because each entity lives in one replica) | `peerClient.collect(uri, MAP_TYPE).responses()` | `MaintenanceReplicaQueries` |
| **Pagination** | Merge everything first, then cut the page in memory | — | `Pages.of` (Flight Routes, `query` package) |
| **Write to an existing entity** (PATCH/PUT/DELETE) | If it is local, update locally (`@Version` → 409 on a clash). Otherwise **forward the command to the owner**: peers are tried one by one, a 404 means "not mine", and a business 4xx from the owner (400/409/422) is propagated with its status and message | `peerClient.forwardToOwner(HttpMethod.PATCH, uri, body, Type.class)` | `command/UpdateFlightRouteService` (Flight Routes), `UpdateAircraftStatusService`, `UpdateAirportService` |
| **Create** | Store it in the replica that received the POST (IDs are UUIDs). Uniqueness checks query the peers and are **best-effort**: if a peer is down, creation continues | `collectList` (Flight Routes: its read model) | `command/CreateFlightRouteService` |
| **Validate a remote entity once** | Only on the replica that received the client's request, not again on peer requests | `peerClient.isPeerRequest()` | `AirportReplicaQueries`, `CompleteMaintenanceRecordService` |

**Ownership rule:** a replica **owns** an entity if and only if the entity is in its local database. That is the replica that received the creating POST, and no other replica has a copy.

**Mechanics (automatic):**
- Peer calls carry `X-Peer-Hops: 1`, the user's JWT, `X-Service-Key` and `X-Correlation-Id`.
- A replica receiving `X-Peer-Hops` answers **with local data only** (`shouldForward()` is false), so no request travels A → B → A.
- When a peer fails or times out (`aisafe.http.aggregation-timeout`), the response gets `X-Partial-Response: true` and `X-Unavailable-Peers: n`. The request does **not** fail.
- Always build `uri` with encoded path segments and query values (`UriComponentsBuilder` / `UriUtils.encodePathSegment`, as in `command/UpdateFlightRouteService`).
- Configure `aisafe.peers.urls` per instance profile (`application-instance{1,2,3}.properties`), listing the **other two** replicas.

**Source:** `COMMON/peers/PeerClient.java`, `COMMON/peers/PeerResult.java`, `COMMON/DistributedInfrastructureAutoConfiguration.java`, `COMMON/http/AisafeHeaders.java`, `FR/flightroutes/command/UpdateFlightRouteService.java`, `FR/flightroutes/command/CreateFlightRouteService.java`, `FR/flightroutes/query/Pages.java`, `code/*/src/main/resources/application-instance*.properties`

---

## 6. Checklist for a provider implementing a contract

- [ ] **Path:** exactly as in this document, under `/api`, declared in the class-level `@RequestMapping`.
- [ ] **Field names:** exactly as in the field tables (camelCase, same spelling). Extra fields are fine; renamed or missing ones are not.
- [ ] **Types and nulls:** numbers as JSON numbers; **no null** in required fields (a null in a field the consumer unboxes causes a 500 on the consumer).
- [ ] **Enum strings:** upper case and exact (`AVAILABLE`, `UNDER_MAINTENANCE`, `OPERATIONAL`, …).
- [ ] **Status codes:** only the ones listed for the bridge (200/404/401/403, plus 409/400 where stated). Body `{"error": "..."}` from your `GlobalExceptionHandler`.
- [ ] **Roles:** your `<Service>AuthorizationRules` allows the roles of the **end users** of the consumer's use cases (the JWT is forwarded).
- [ ] **Peer behaviour:** a GET by ID finds entities stored in any replica (`findFirst`), collections are aggregated (`collectList`), and writes go to the owner (`forwardToOwner`).
- [ ] **Configuration:** `aisafe.peers.urls` per instance; the same `jwt.secret` and `aisafe.security.service-key` as every other service.
- [ ] **Service-only endpoints:** if an endpoint is only for other services, add it to `aisafe.security.service-only-paths` (section 2.8); in Postman, call it with `X-Service-Key: {{serviceKey}}` and add a request without the key that expects 401.
- [ ] **`AuthorizationRules`:** list only your own endpoints and never call `anyRequest()` (the `common` chain adds `anyRequest().authenticated()`; a service that calls it does not start).
- [ ] **Postman:** one request per provided endpoint in your service's collection (`docs/5-scenarios/<service>/postman/`), including a 404 case and a request to a replica that does not own the entity.
- [ ] **Contract test:** call the consumer against your real service (or update the consumer's stub, e.g. the stub in `ReplicaCollaborationTest`) with the example JSON from this document.

---

## 7. Decisions (closed)

Every question that was open is now closed. Each entry records the outcome **as implemented in the code**.

- **Q1. Status after maintenance (US119).** `AVAILABLE` (not `ACTIVE`), the only value Flight Routes accepts for scheduling. Maintenance sets it only when the aircraft has no other ongoing record in any replica **and** every replica answered; otherwise the aircraft stays `UNDER_MAINTENANCE` (`AircraftAvailabilityService.releaseIfNoOtherOngoing`).
- **Q2. Airport status values.** `OPERATIONAL`, `CLOSED`, `UNDER_MAINTENANCE` (Airports, US109). Flight Routes treats any value other than `OPERATIONAL` as "not operational" (`AirportSnapshot.isOperational`).
- **Q3. Airport certifications in US212.** **Not checked.** Airports stores and returns `certifiedModels` (US106a), but Flight Routes does not read the field and US212 does not check it. See the known limitations (section 8).
- **Q4. Roles.** The JWT of the end user is forwarded, so each provider allows the roles of the consumer's use case. As implemented:
  - **Aircraft Management:** `POST /api/aircraft-models` (US101) `BACKOFFICE_OPERATOR`; `POST /api/aircrafts` (US102) `ATCC`; `PATCH /api/aircrafts/*/status` (US105, B3b) `ATCC`, `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR`; `GET /api/aircrafts/operational-hours` (US206) and `GET /api/aircrafts/*/compatible-routes` (US203) `ATCC`; `GET /api/aircraft-models/top-utilized` (US204) `BACKOFFICE_OPERATOR`, `ATCC`; other `GET /api/aircrafts/**` and `GET /api/aircraft-models/**` (US103, US104, B1, B3a, B3c, B3d) `ATCC`, `BACKOFFICE_OPERATOR`, `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR`.
  - **Airports:** `POST /api/airports`, `PATCH /api/airports/*`, `PATCH /api/airports/*/status` and `GET /api/airports/statistics/**` (US210) `BACKOFFICE_OPERATOR`; `GET /api/airports` (US108) and `GET /api/airports/grouped` (US211) `ATCC`; `GET /api/airports/*` (US107, B2) `BACKOFFICE_OPERATOR`, `ATCC`; `POST /api/airports/*/certifications` (US106a) `BACKOFFICE_OPERATOR`, `ATCC`; anything else under `/api/airports` is denied.
  - **Flight Routes:** `POST /api/routes`, `GET /api/routes/**`, `POST /api/scheduled-flights` and `GET /api/scheduled-flights/**` `ATCC`; `PATCH /api/routes/*` `ATCC`, `BACKOFFICE_OPERATOR`; `GET /api/routes/statistics/count-by-airport` (B5) `BACKOFFICE_OPERATOR`; `GET /api/routes/compatible` (B6) `ATCC`, `MAINTENANCE_SUPERVISOR`, `MAINTENANCE_TECHNICIAN`; **`GET /api/scheduled-flights/statistics/minutes-by-aircraft` (B4b) `ATCC`, `BACKOFFICE_OPERATOR`**, so US204 also works for the Backoffice Operator.
  - **Maintenance** *(decided by Maintenance)*: US115/US119/US217 `MAINTENANCE_TECHNICIAN`; US116/US218 technician and supervisor; US219/US221 `MAINTENANCE_SUPERVISOR`; US117/US220/US222 `ATCC` — see [Maintenance L3 §3](../3-components/maintenance/Maintenance-Components.md#3-provided-interfaces-rest-api).
- **Q5. "Most utilised" (US204).** Flight time: the minutes from B4b summed per model. No flights-by-aircraft endpoint exists.
- **Q6. INACTIVE routes in US210.** Counted. `count-by-airport` has no status filter (`ReplicatedRouteRepository.countByOrigin`/`countByDestination`).
- **Q7. B3b transitions and ordering.** `IN_FLIGHT` → `UNDER_MAINTENANCE`/`INACTIVE` is rejected with 409 (`Aircraft.changeStatus`); the server-side `@Version` check is enough (no `If-Match`). US115: Maintenance flushes its record, then calls B3b, then commits, and sends a compensating PATCH if the transaction rolls back after B3b. US119: the record is completed first and the release is a separate step (section B3b).
- **Q8. Page formats.** Each service keeps its own format (section 2.6): Aircraft Management and Maintenance return `PageDTO` (`content`, `page`, `size`, `totalElements`, `totalPages`); Flight Routes returns a HAL `PagedModel` for US213 and the Spring Data `Page` JSON (`direct` mode) for B4a. Because B4a has no consumer, no service depends on the `direct` format; the only cross-service page consumer (Maintenance, B3d) reads Aircraft Management's `PageDTO`.
- **Q9. Units of range.** Kilometres. Route distances are Haversine distances with an Earth radius of 6371 km (`DistanceCalculatorService`), and `minRange`/`maximumRange` are compared directly in the same unit.
- **Q10. Registration format.** Aircraft Management validates `^[A-Z0-9]{1,2}-[A-Z0-9]{2,5}$` on `POST /api/aircrafts` (US102), so stored registrations are upper case (e.g. `CS-TUA`). Maintenance trims and upper-cases the registration before calling B3a; Flight Routes sends it as typed (see section 8).
- **Q11. Inter-service authentication.** `X-Service-Key` is sent on every inter-service call. It is required on **peer calls** and on the **service-only endpoints** declared in `aisafe.security.service-only-paths` (Flight Routes: B4a, B4b, B5, B6 and `usage-by-route`, section 2.8). Endpoints that also serve end users (B1, B2, B3) accept a valid JWT without the key.

## 8. Known limitations

What the contracts or the decisions above promise, but the code does not do (or does only partly):

- **Certifications not checked in US212 (Q3).** A flight can be scheduled to or from an airport that is not certified for the aircraft model.
- **No stale-version detection in the API.** Entities have `@Version` and a write clash returns 409, but no update endpoint accepts a version: `PATCH /api/routes/{id}` (Flight Routes), `PATCH /api/aircrafts/{reg}/status` (Aircraft Management), `PATCH /api/airports/{iata}` (Airports) and the Maintenance record commands take no `version`, `ETag` or `If-Match`, and only Maintenance returns `version` in its DTO. A client that updates from stale data is not told (last writer wins). A 409 only happens when two updates overlap on the owner replica (demonstrated by folder 8 of the Flight Routes Postman collection).
- **Registrations are not normalised by Flight Routes.** Aircraft Management looks registrations up exactly (case-sensitive), so a lower-case registration typed in US212/US213 gives 404, while Maintenance normalises it.
- **B4, B5 and B6 may be slightly stale (Assignment 2).** Flight Routes answers them from its read models: never partial, but a route or flight created on another Flight Routes replica appears only when its event arrives (milliseconds; longer if that replica's event was lost, see [Containers §3.6](Containers.md#36-known-limitations-of-messaging)). The Assignment 1 limitation "partial counts in US210 are not flagged" no longer applies.
- **B4a has no consumer** and still uses the Spring Data `direct` page JSON, which Spring does not guarantee to keep stable.
- **Cross-replica uniqueness is best-effort** (AP, see [Containers §3.3](Containers.md#33-consistency-model-cap)): during a partition two replicas can accept the same route, airport or aircraft. In Flight Routes the check runs on the read model, so two replicas can also accept the same route inside the eventual-consistency window.
- **Development defaults.** `POST /auth/dev-token` is enabled by default (`AISAFE_DEV_TOKEN`, default `true`) and `application.properties` contains default values for `jwt.secret` and `aisafe.security.service-key`; they must be overridden by environment variables outside development.

---

## 9. Assignment 2 — event contracts

External clients keep using the REST endpoints of sections 3–4; events are used **between services and between replicas** of a service, through RabbitMQ. Everything below is implemented in the shared library (`code/common`, package `isep.sidis.common.messaging`); Flight Routes is the working example (`code/flightroutes`, packages `command` and `query`). **Copy it; do not write your own AMQP code.** Starting the broker: [docs/README.md, "How to build and run"](../README.md#how-to-build-and-run). Reliability, consistency, the topic-exchange decision and the known limitations of messaging: [Containers §3.3–3.6](Containers.md#33-consistency-model-cap).

### 9.1 Event envelope

Every message body is an `IntegrationEvent<T>` (Java record in `common`), serialised as JSON:

| Field | Type | Meaning |
|---|---|---|
| `eventId` | UUID string | Unique id of this event. Consumers use it to **ignore duplicates** |
| `eventType` | string | What happened, in past tense, PascalCase: `RouteCreated`, `AircraftStatusChanged` |
| `timestamp` | ISO-8601 string, UTC | When the event was created, e.g. `2026-11-10T14:03:21.512Z` |
| `sourceService` | string | Producing service = its `spring.application.name`, e.g. `flightroutes` |
| `sourceInstance` | string | Producing replica = its `aisafe.instance-id`, e.g. `flightroutes-1` |
| `aggregateId` | string | Id of the entity the event is about: route id, registration, IATA code, ... |
| `payload` | object | Event data. Defined and documented by the producer |

```json
{
  "eventId": "5b0f6c1e-2a4b-4f7e-9d6a-1c2b3d4e5f60",
  "eventType": "RouteCreated",
  "timestamp": "2026-11-10T14:03:21.512Z",
  "sourceService": "flightroutes",
  "sourceInstance": "flightroutes-1",
  "aggregateId": "8c1d2f9e-4b7a-4e43-9f0e-2d6a1b3c5e77",
  "payload": {
    "routeId": "8c1d2f9e-4b7a-4e43-9f0e-2d6a1b3c5e77",
    "originIata": "OPO", "destinationIata": "LIS",
    "distance": 274.0, "minRange": 400.0, "minCapacity": 120, "estimatedFlightTime": 55,
    "status": "ACTIVE", "version": 0,
    "history": [ { "changeDate": "2026-11-10T14:03:21.4", "description": "Route created.",
                   "newMinRange": 400.0, "newMinCapacity": 120, "newEstimatedFlightTime": 55, "newStatus": "ACTIVE" } ]
  }
}
```

Rules:
- **You never fill the envelope fields by hand.** `EventPublisher.publish(type, key, id, payload)` builds it. A producer that must apply its own event before publishing it (Flight Routes, see [its L3 §6](../3-components/flightroutes/FlightRoutes-Components.md#6-cqrs-flow-write--event--projection-on-every-replica)) builds it with `IntegrationEvent.create(...)` and publishes that same instance with `EventPublisher.publish(event, routingKey)`, so the `eventId` stays the same.
- **Payloads carry state, not just ids** ("state-carrying events"): put in the payload everything a consumer needs, so it never has to call you back over HTTP. Every payload carries the entity's `@Version` as `version`.
- **Tolerant reader:** consumers ignore fields they do not know. You may **add** payload fields at any time. **Renaming or removing** a field breaks consumers: agree on it with them first.
- The AMQP message also has `message_id` = `eventId`, `type` = `eventType`, `app_id` = `sourceService`, persistent delivery mode and, if the event comes from an HTTP request, the header `X-Correlation-Id`, so the logs of producer and consumer can be matched.
- JSON uses Jackson 3 (Spring Boot 4). `java.time` support is built into Jackson 3, so the Jackson 2 `JavaTimeModule` is not needed. Dates are written as ISO strings, never as numbers (`EventMessageConverters`).

### 9.2 Exchanges, routing keys and queues

Names are built by `MessagingNames` (`common`). Name parts are lower case, digits and `-`, with **no dots**, because dots separate the parts.

| Object | Rule | Example |
|---|---|---|
| Events exchange: **topic**, durable. One per **producing** service | `aisafe.<service>.events` | `aisafe.flightroutes.events` |
| Routing key | `<aggregate>.<action>` | `route.created`, `scheduled-flight.created` |
| Queue: durable, non-exclusive, not auto-delete. One per **consuming instance** per source service | `<instanceId>.<sourceService>` | `flightroutes-2.flightroutes` (replica 2 of Flight Routes, reading Flight Routes' events) |
| Dead-letter exchange: direct, durable, **shared** | `aisafe.dlx` | `aisafe.dlx` |
| Dead-letter queue (DLQ): one per consumer queue, bound to `aisafe.dlx` with the queue name as routing key | `<instanceId>.<sourceService>.dlq` | `flightroutes-2.flightroutes.dlq` |

`<instanceId>` is the replica's `aisafe.instance-id` exactly as configured (`flightroutes-2`). It already contains the consuming service name, so the queue name says "who reads" (`flightroutes-2`) and "whose events" (`flightroutes`). Consequences of "one queue per instance":
- **Every replica receives every matching event** (each has its own copy in its own queue). This is what each replica needs to keep its own copy of the data in its own database.
- If a replica is down, its events **wait in its queue** and are processed when it comes back.
- Queues are **not** shared by the replicas of a service (that would be the "competing consumers" pattern, where each event goes to only one replica).

Binding patterns (topic syntax): `*` = exactly one word, `#` = zero or more words. `route.*` = every route event; `scheduled-flight.created` = only that one; `#` = everything from that exchange.

| Exchange (topic, durable) | Publisher | Routing keys | Queue per consuming instance |
|---|---|---|---|
| `aisafe.flightroutes.events` | Flight Routes (owner replica of the entity) | `route.created`, `route.updated`, `scheduled-flight.created`, `scheduled-flight.status-changed` (reserved) | `<instanceId>.flightroutes`, e.g. `flightroutes-2.flightroutes`, `airports-1.flightroutes` |
| `aisafe.aircraftmanagement.events` | Aircraft Management (**to implement**) | `aircraft.registered`, `aircraft.status-changed`, `aircraft-model.created` | `<instanceId>.aircraftmanagement`, e.g. `flightroutes-1.aircraftmanagement`, `maintenance-1.aircraftmanagement` |
| `aisafe.airports.events` | Airports (**to implement**) | `airport.created`, `airport.updated`, `airport.status-changed` | `<instanceId>.airports`, e.g. `flightroutes-1.airports` |
| `aisafe.maintenance.events` | Maintenance (optional, not needed by any bridge) | e.g. `maintenance-record.started`, `maintenance-record.completed` | — |
| `aisafe.dlx` (direct, shared) | the broker (dead-lettering) | the consumer queue name | `<queue>.dlq` |

Picture for Flight Routes with 3 replicas:

```text
                         route.created / route.updated / scheduled-flight.created
flightroutes-N ──publish──▶ [aisafe.flightroutes.events] (topic)
                                 │ route.*, scheduled-flight.*   (same bindings on each queue)
                                 ▼                       ▼                       ▼
             flightroutes-1.flightroutes   flightroutes-2.flightroutes   flightroutes-3.flightroutes
                 │ rejected after retries
                 ▼
            [aisafe.dlx] ──▶ flightroutes-1.flightroutes.dlq
```

A service that wants route events (e.g. Airports) **adds its own queues** (`airports-1.flightroutes`, ...) bound to the same exchange. The producer does not change.

### 9.3 Events published by Flight Routes

Published today, on `aisafe.flightroutes.events`, after the commit, only by the replica that **owns** the entity (other replicas forward commands to it), so the events of one entity come from one producer, in order.

| `eventType` | Routing key | Published when | Payload (record) | Consumers (status) |
|---|---|---|---|---|
| `RouteCreated` | `route.created` | US110 `POST /api/routes` (and demo bootstrap), by the replica that stored the route (its owner) | `RouteEventPayload`: `routeId`, `originIata`, `destinationIata`, `distance` (km), `minRange` (km), `minCapacity`, `estimatedFlightTime` (min), `status` (`ACTIVE`/`INACTIVE`), `version`, `history[]` (`changeDate`, `description`, `previous*`/`new*` of the attributes that changed) | Flight Routes replicas: `REPLICATED_ROUTE` (**implemented**). Airports: count per airport, replaces B5 (**possible now**). Aircraft Management: compatible routes, replaces B6 (**possible now**) |
| `RouteUpdated` | `route.updated` | US112 `PATCH /api/routes/{id}` (details or `ACTIVE`/`INACTIVE`), by the **owner** | same | same |
| `ScheduledFlightCreated` | `scheduled-flight.created` | US212 `POST /api/scheduled-flights` (and demo bootstrap), by the replica that stored the flight | `ScheduledFlightEventPayload`: `flightId`, `aircraftRegistration`, `routeId`, `originIata`, `destinationIata`, `estimatedFlightTime` (min, copied from the route), `date` (`yyyy-MM-dd`), `time` (`HH:mm:ss`), `status`, `version` | Flight Routes replicas: `REPLICATED_SCHEDULED_FLIGHT` (**implemented**). Aircraft Management: minutes per aircraft, replaces B4b (**possible now**). Maintenance: flight hours of B3d (**possible now**) |
| `ScheduledFlightStatusChanged` | `scheduled-flight.status-changed` | **reserved**: the API cannot change a flight's status yet. The read model already applies it | same | same |

There is **no `RouteDeleted`**: routes are never deleted. Deactivating a route is a `RouteUpdated` with `status = "INACTIVE"`. Code: `flightroutes/.../events/RouteEvents.java`, `RouteEventPayload.java`, `ScheduledFlightEvents.java`, `ScheduledFlightEventPayload.java`.

### 9.4 Events expected from the other services

These contracts are **agreed but not yet published**. Flight Routes already has the consumer: queues `<instanceId>.aircraftmanagement` and `<instanceId>.airports`, projections into `KNOWN_AIRCRAFT`, `KNOWN_AIRCRAFT_MODEL` and `KNOWN_AIRPORT`, and lookups that use them before HTTP. It is **off** by default and switched on with `AISAFE_EXTERNAL_EVENTS=true` (`aisafe.flightroutes.external-events.enabled`) once the producers publish. It then uses the local copy first and falls back to HTTP for an entity it has not seen yet, so switching it on never makes a request fail. While off, bridges B1 and B2 stay synchronous HTTP. Code: `flightroutes/.../events/external/`, `query/external/`, `infrastructure/messaging/ExternalEventsMessagingConfiguration.java`, test `ExternalEventsScaffoldingTest.java`.

| Producer → exchange | `eventType` | Routing key | When | Payload (all fields required unless marked) | Consumers (status) |
|---|---|---|---|---|---|
| Aircraft Management → `aisafe.aircraftmanagement.events` | `AircraftRegistered` | `aircraft.registered` | US101/US102 aircraft registered | `registrationNumber`, `modelName`, `seatingCapacity`, `status` (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`), `version` | Flight Routes: `KNOWN_AIRCRAFT`, replaces B1a (**consumer ready, off**). Maintenance: replaces B3a and the fleet of B3d. Unicorns projection ([Unicorns-Mock.md](Unicorns-Mock.md)) |
| | `AircraftStatusChanged` | `aircraft.status-changed` | US105 status changed, including the change requested by Maintenance (B3b) | same fields; `modelName` and `seatingCapacity` may be omitted (null keeps the stored value) | same |
| | `AircraftModelCreated` | `aircraft-model.created` | aircraft model registered | `modelName`, `maximumRange` (km), `version` | Flight Routes: `KNOWN_AIRCRAFT_MODEL`, replaces B1b (**consumer ready, off**). Maintenance: replaces B3c |
| Airports → `aisafe.airports.events` | `AirportCreated` | `airport.created` | US107 airport registered | `iataCode`, `latitude`, `longitude`, `status` (`OPERATIONAL`, `CLOSED`, `UNDER_MAINTENANCE`), `version` | Flight Routes: `KNOWN_AIRPORT`, replaces B2 (**consumer ready, off**) |
| | `AirportUpdated` | `airport.updated` | airport details updated | same | same |
| | `AirportStatusChanged` | `airport.status-changed` | operational status changed | same; `latitude`/`longitude` may be omitted | same |

`aggregateId` = `registrationNumber`, `modelName` or `iataCode`. `version` = the entity's `@Version` (older events are ignored).

### 9.5 How to publish and consume events

**Common steps (publisher and consumer).**

1. **Dependency** (`code/<service>/pom.xml`). `common` declares AMQP as *optional*, so add it yourself:

   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-amqp</artifactId>
   </dependency>
   ```

2. **Configuration** (`code/<service>/src/main/resources/application.properties`). Copy this block as is:

   ```properties
   aisafe.messaging.enabled=${AISAFE_MESSAGING_ENABLED:true}
   spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
   spring.rabbitmq.port=${RABBITMQ_PORT:5672}
   spring.rabbitmq.username=${RABBITMQ_USER:}
   spring.rabbitmq.password=${RABBITMQ_PASSWORD:}
   spring.rabbitmq.publisher-confirm-type=simple
   aisafe.messaging.max-attempts=3
   aisafe.messaging.initial-interval=500ms
   aisafe.messaging.multiplier=2.0
   aisafe.messaging.max-interval=5s
   management.health.rabbit.enabled=${aisafe.messaging.enabled}
   # RabbitMQ only in READINESS: a replica whose broker is down stays live and keeps serving HTTP
   management.endpoint.health.probes.enabled=true
   management.endpoint.health.validate-group-membership=false
   management.endpoint.health.group.liveness.include=livenessState,ping
   management.endpoint.health.group.readiness.include=readinessState,rabbit
   ```

   With this block the common library declares your exchange `aisafe.<spring.application.name>.events` at startup and gives you an `EventPublisher` bean. `scripts/start-all.*` waits for `/actuator/health/liveness` (and falls back to `/actuator/health` for services without the probe groups).

3. **Keep your existing tests broker-free.** Create `src/test/resources/config/application.properties` with `aisafe.messaging.enabled=false`. Spring Boot loads `config/application.properties` *on top of* your main file (it does not replace it), so only this key changes in tests.

**Publish: raise an in-process event inside your transaction and publish it AFTER the commit.** Publishing inside the transaction could announce a change that is then rolled back.

```java
// 1) In your write service (already @Transactional): raise an in-process Spring event.
//    With messaging off nobody listens to it, so this is always safe.
@Service
@RequiredArgsConstructor
public class UpdateAircraftStatusService {
    private final ApplicationEventPublisher events;
    // ...
    @Transactional
    public AircraftDTO updateStatus(String registration, String newStatus) {
        Aircraft saved = repository.saveAndFlush(aircraft);       // flush: the version is final
        events.publishEvent(new AircraftStatusChanged(saved));   // your own small record
        return assembler.toDTO(saved);
    }
}

// 2) After the commit, turn it into an integration event.
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AircraftIntegrationEventPublisher {
    private final EventPublisher eventPublisher;   // from common

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AircraftStatusChanged change) {
        Aircraft a = change.aircraft();
        try {
            eventPublisher.publish(
                    "AircraftStatusChanged",                                  // eventType
                    MessagingNames.routingKey("aircraft", "status-changed"),  // aircraft.status-changed
                    a.getRegistrationNumber(),                                // aggregateId
                    new AircraftEventPayload(a.getRegistrationNumber(), a.getModelName(),
                            a.getSeatingCapacity(), a.getStatus().name(), a.getVersion()));   // payload (a record)
        } catch (AmqpException e) {
            log.error("Aircraft {} saved but the event could not be published: {}", a.getRegistrationNumber(), e.getMessage());
        }
    }
}
```

If your service also keeps its **own** read model (CQRS, like Flight Routes), use the Flight Routes variant instead: `command/FlightRoutesEventEmitter` builds the event, applies it to the local read model in the same transaction, and `command/IntegrationEventRelay` publishes it after the commit.

Publisher checklist:
- [ ] `eventType` in past tense (`...Created`, `...Updated`, `...StatusChanged`).
- [ ] Routing key via `MessagingNames.routingKey(aggregate, action)`.
- [ ] Payload is a Java `record` with the full state and a `version` (your entity's `@Version`), so consumers can drop stale events.
- [ ] Publish only from the replica that **owns** the entity (where the write happened), never from a replica that forwarded the command.
- [ ] Add the event to section 9.3/9.4 of this document and to your L3 document, and tell the consumers.

**Consume, step 1: subscribe this instance.** This declares your queue, its DLQ and the bindings:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class MessagingConfiguration {

    /** Queue <instanceId>.flightroutes (e.g. airports-1.flightroutes), bound to route.created and route.updated. */
    @Bean
    public EventSubscription flightRoutesEvents(MessagingTopology topology) {
        return topology.subscribe("flightroutes", "route.*");
    }
}
```

**Consume, step 2: declare the payload you need** as a record with only the fields you read. Extra fields in the JSON are ignored:

```java
public record RoutePayload(String routeId, String originIata, String destinationIata, String status, Long version) { }
```

**Consume, step 3: listen and apply idempotently, in one transaction.**

```java
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RouteEventsListener {
    private final RouteCountProjection projection;

    // The bean name of step 1 + ".queueName"
    @RabbitListener(queues = "#{flightRoutesEvents.queueName}")
    public void on(IntegrationEvent<RoutePayload> event) {   // the generic type is resolved from this signature
        projection.apply(event);
    }
}

@Service
public class RouteCountProjection {
    private final IdempotentEventHandler idempotent;
    // + your repositories

    public RouteCountProjection(ProcessedEventStore processedEvents /* , repositories */) {
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    @Transactional   // the change and the "already processed" record commit together
    public void apply(IntegrationEvent<RoutePayload> event) {
        idempotent.handle(event, e -> {
            switch (e.eventType()) {
                case "RouteCreated", "RouteUpdated" -> upsert(e.payload());   // insert or update by id, unless older version
                default -> { }                                                // unknown type: ignore
            }
        });
    }
}
```

If one queue carries **several payload types** (all the events of one producer), receive `IntegrationEvent<tools.jackson.databind.JsonNode>` and convert the payload by `eventType` (`FlightRoutesEventRouter.withPayload` in Flight Routes).

**Consume, step 4 (recommended): remember processed events in your database.** The default `ProcessedEventStore` is in memory. With a JPA implementation the "event applied" and "event id recorded" steps share one transaction. Copy `flightroutes/.../infrastructure/messaging/ProcessedEvent.java`, `ProcessedEventRepository.java` and `JpaProcessedEventStore.java` (3 small classes). Declaring it as a `@Component` replaces the in-memory default.

Rules for handlers:
- **Return normally = success.** The message is acknowledged (ack) only after your method returns.
- **Throw = failure.** The message is retried (`aisafe.messaging.max-attempts`, default 3, with back-off) and then moved to your DLQ. Do not catch and hide exceptions you cannot handle.
- **Be idempotent anyway:** use upserts by id. Use `version` to ignore events older than what you already have (`ReplicatedRoute.isNewerThan` in Flight Routes).
- **Unknown `eventType`: ignore it** (log it). Producers may add event types.
- **Your own events:** with one queue per instance, a service that subscribes to its own exchange also receives the events it published. Flight Routes has already applied them (same `eventId`), so they are skipped as duplicates.
- Do not call other services over HTTP inside a handler. If you need their data, subscribe to their events.

Reference: `flightroutes/.../infrastructure/messaging/FlightRoutesMessagingConfiguration.java`, `query/FlightRoutesEventsListener.java`, `query/FlightRoutesEventRouter.java`, `query/RouteProjection.java`, `query/ScheduledFlightProjection.java`. Do not override the reliability settings of `common` (`EventMessagingAutoConfiguration`, `ConsumerRetry`, `EventSubscription`) per service without telling the team.

### 9.6 Status of every bridge

| Bridge | Assignment 2 status | Why / what is missing |
|---|---|---|
| B1a, B1b (Flight Routes → Aircraft Management) | `HTTP (waiting for Aircraft Management events)` | Flight Routes' consumer (`KNOWN_AIRCRAFT`, `KNOWN_AIRCRAFT_MODEL`) is implemented and tested in-process; Aircraft Management must publish `AircraftRegistered`, `AircraftStatusChanged` and `AircraftModelCreated`. Note: US212 checks availability on Flight Routes' copy; Aircraft Management stays the source of truth (an aircraft that went `UNDER_MAINTENANCE` a few ms earlier can still be scheduled, see section 9.7). |
| B2 (Flight Routes → Airports) | `HTTP (waiting for Airports events)` | Consumer (`KNOWN_AIRPORT`) implemented and tested in-process; Airports must publish `AirportCreated`, `AirportUpdated`, `AirportStatusChanged`. |
| B3a, B3c (Maintenance → Aircraft Management) | `HTTP (waiting for Aircraft Management events)` | Maintenance needs a local copy of aircraft and models fed by the same events. |
| B3b (Maintenance → Aircraft Management, status change) | `HTTP (kept)` | It is a **command** on Aircraft Management's aggregate with an immediate answer (409 if the transition is invalid, e.g. `IN_FLIGHT` → `UNDER_MAINTENANCE`) and a compensation on rollback (Q7). An event would make the outcome asynchronous and lose that answer. Aircraft Management then publishes `AircraftStatusChanged`, so the *effect* reaches everyone by event. |
| B3d (Maintenance → Aircraft Management, fleet and hours) | `HTTP (waiting for Aircraft Management events)` | The fleet can come from aircraft events; the hours come from Flight Routes' `ScheduledFlightCreated` (already published). |
| B4a (→ Flight Routes, operational hours page) | `HTTP (provider uses read model)` | No consumer; answered from `REPLICATED_SCHEDULED_FLIGHT`. |
| B4b (Aircraft Management → Flight Routes, minutes per aircraft) | `HTTP (provider uses read model)`; `EVENT-BASED` possible now | Flight Routes answers from its read model (never partial). Aircraft Management can drop the call by consuming `ScheduledFlightCreated` and summing `estimatedFlightTime` per `aircraftRegistration`. |
| B5 (Airports → Flight Routes, routes per airport) | `HTTP (provider uses read model)`; `EVENT-BASED` possible now | Same; Airports can count `originIata`/`destinationIata` of `RouteCreated` (routes are never deleted; `RouteUpdated` does not change the airports). |
| B6 (Aircraft Management → Flight Routes, compatible routes) | `HTTP (provider uses read model)`; `EVENT-BASED` possible now | Same; Aircraft Management can keep active routes (`status`, `minRange`, `minCapacity`) from `RouteCreated`/`RouteUpdated` and filter locally. Kept as HTTP until then because it is a query whose filter depends on the request. |

### 9.7 Consistency of the event-based bridges

A consumer that decides on its local copy decides on data that may be **milliseconds old**. Where that matters, the **owner re-validates**:
- US212 (scheduling) on Flight Routes' copy of an aircraft: if Aircraft Management has just set it `UNDER_MAINTENANCE`, the flight can still be accepted. Accepted risk for the assignment; the fix is a compensation (Flight Routes consuming `AircraftStatusChanged` and flagging the future flights of that aircraft, or Maintenance refusing to start while flights are scheduled).
- Counts and statistics (B4b, B5) are informative: a slightly stale value is acceptable.
