# C4+1 — Aircraft Management Service: Scenarios

> The "+1" view: real use cases and end-to-end behaviour across containers and components. Each scenario validates [L2](../../2-containers/Containers.md), [L3](../../3-components/aircraftmanagement/AircraftManagement-Components.md) and [L4](../../4-code/aircraftmanagement/AircraftManagement-Code.md). Reference: [Flight Routes — Scenarios](../flightroutes/FlightRoutes-Scenarios.md).

## 1. User stories

| WP | US | Description | Actor | Status |
|---|---|---|---|---|
| 1A | US101 | Register an aircraft model (manufacturer, model name, seating capacity, fuel capacity, maximum range, cruising speed) | Backoffice Operator | Implemented |
| 1A | US102 | Register an aircraft (registration number, model, manufacturing date, status) | ATCC | Implemented |
| 1A | US103 | View an aircraft's details given its registration number | Backoffice Operator, ATCC | Implemented |
| 1A | US104 | Search aircraft by model, status or manufacturing year | ATCC | Implemented |
| 1A | US105 | Update an aircraft's operational status | ATCC | Implemented |
| 1B | US201 | Update an aircraft model's specifications | Backoffice Operator | Planned |
| 1B | US202 | Register an aircraft model with an optional image or technical diagram | Backoffice Operator | Planned |
| 1B | US203 | View the routes compatible with an aircraft (range of the model, capacity of the aircraft) | ATCC | Implemented |
| 1B | US204 | Top 5 most utilised aircraft models | Backoffice Operator | Implemented |
| 1B | US205 | Real-time aircraft availability status (available, in-flight, under maintenance, inactive) | ATCC | Planned |
| 1B | US206 | Total operational hours of each aircraft of the fleet | ATCC | Implemented |
| Bonus | US223, US224 | Utilisation graphs; search by features (Wi-Fi, engine type) | ATCC | Planned |

**Supplied to other containers** (the consumers depend on them): **US103** is contract **B1a** and **US101/US103** give **B1b** (Flight Routes, US212/US213); **US103** is also **B3a** and **US105** is **B3b** (Maintenance, US115/US119). See [Integration-Contracts §4](../../2-containers/Integration-Contracts.md#4-bridges).

### Decisions on open points

- **Statuses (Integration-Contracts Q1):** `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`. Only `AVAILABLE` lets Flight Routes schedule flights, so Maintenance returns an aircraft to `AVAILABLE` (not `ACTIVE`).
- **Status transitions (Q7, proposal):** an `IN_FLIGHT` aircraft cannot go to `UNDER_MAINTENANCE` or `INACTIVE` (`409`); every other change is accepted. Optimistic locking (`@Version`) protects concurrent updates in the owner replica; the client does not send a version.
- **Units (Q9):** `maximumRange` in **km**, the same unit as the route distances computed by Flight Routes.
- **Registration format (Q10, proposal):** `PREFIX-SUFFIX`, upper case, e.g. `CS-TUA` (`^[A-Z0-9]{1,2}-[A-Z0-9]{2,5}$`), checked when an aircraft is registered. To be confirmed with the customer.
- **"Most utilised" (Q5):** total **flight time** (minutes) of all the aircraft of the model, from contract B4b. Models without flights are not ranked.
- **Pagination (Q8):** a stable `PageDTO` (`content`, `page`, `size`, `totalElements`, `totalPages`) instead of Spring's `Page`; maximum size 100.
- **Roles (Q4):** US102 → ATCC; US101 → Backoffice Operator; reads (US103/US104, B1, B3a) also for the Backoffice and Maintenance roles because the JWT of the consumer's user is forwarded; US105/B3b → ATCC and Maintenance roles; US203/US206 → ATCC; US204 → Backoffice Operator and ATCC (Flight Routes admits both roles on `minutes-by-aircraft`, B4b).
- **Open question Q-AM1 (US205):** what is "in-flight"? Today `IN_FLIGHT` is only a status that can be set through US105. A real-time value would be derived from the scheduled flights of Flight Routes (a flight whose date/time interval contains *now*), which needs a new endpoint there. To be decided with the customer and the Flight Routes owner before implementing.

## 2. Acceptance criteria

**Fleet:** AC1 a model needs a non-blank name and manufacturer and strictly positive seats, fuel, range and speed (else `400`, body = field → message); AC2 a model name found in any replica that answers → `409`; AC3 an aircraft needs a registration in the agreed format, an existing model (in any replica, else `400`), positive seats and a manufacturing date that is not in the future (else `400`); AC4 a registration found in any replica that answers → `409`; AC5 an aircraft is created `AVAILABLE` unless a valid status is sent, `201`; AC6 unknown status → `400`; AC7 `IN_FLIGHT` → `UNDER_MAINTENANCE`/`INACTIVE` → `409`; AC8 concurrent updates of the same aircraft → `409`.

**Queries:** AC9 US104 filters (`modelName`, `status`, `manufacturingYear`) are optional and combinable, results sorted by registration, paginated **after** merging all replicas (`page` ≥ 0, `size` 1–100, else `400`); AC10 US203 uses the model's range and the aircraft's own capacity, only `ACTIVE` compatible routes of all Flight Routes replicas; AC11 US204 ranks models by total flight minutes (ties by model name), `limit` 1–20 (default 5), models without flights are excluded; AC12 US206 lists **every** aircraft of the fleet, with 0 hours when it has no flights.

**Distribution:** AC13 any replica answers with the data of all replicas, `404` if no replica has the record; AC14 a failed peer gives a `200` with `X-Partial-Response: true`; AC15 an aircraft/model is only modified by its owner replica (the one that stored it); AC16 peer requests without a valid `X-Service-Key` → `401`, `X-Peer-Hops > 1` → `400`; AC17 JWT + roles on every endpoint (`401`/`403`), every request audited with the correlation id; AC18 `503` when no Flight Routes replica answers (US203, US204, US206) or no peer is reachable to execute a command; AC19 if Flight Routes answers partially, so does the statistics response (`X-Partial-Response: true`); AC20 the contract fields of B1a/B1b are never `null`.

## 3. System sequence diagram

"System" = any of the three replicas.

![AircraftManagement-SSD](svg/AircraftManagement-SSD.svg)

## 4. Scenarios (sequence diagrams)

### 4.1 Distributed scenarios

**S1 — Peer-to-peer query resolution: GET an aircraft stored in another replica (US103, contract B1a; the same for B1b)**

![AircraftManagement-SD-PeerForwarding](svg/AircraftManagement-SD-PeerForwarding.svg)

**S2 — Response aggregation with a failed replica (US104; the fleet of US203/US204/US206 is built the same way)**

![AircraftManagement-SD-PeerAggregation](svg/AircraftManagement-SD-PeerAggregation.svg)

**S3 — Command forwarded to the owner replica (US105, contract B3b)**

![AircraftManagement-SD-OwnerCommand](svg/AircraftManagement-SD-OwnerCommand.svg)

### 4.2 Use-case scenarios

**S4 — Register aircraft whose model is stored in another replica (US102)**

![AircraftManagement-SD-CreateAircraft](svg/AircraftManagement-SD-CreateAircraft.svg)

**S5 — Operational hours of the fleet (US206, contract B4b with Flight Routes)**

![AircraftManagement-SD-OperationalHours](svg/AircraftManagement-SD-OperationalHours.svg)

**S6 — Top utilised models (US204, contract B4b)**

![AircraftManagement-SD-TopUtilizedModels](svg/AircraftManagement-SD-TopUtilizedModels.svg)

**S7 — Compatible routes (US203, contract B6)**

![AircraftManagement-SD-CompatibleRoutes](svg/AircraftManagement-SD-CompatibleRoutes.svg)

## 5. Tests

From `code/`: `./mvnw -pl aircraftmanagement -am test`.

**Existing**

| Test class | Type | What is verified |
|---|---|---|
| [`AircraftManagementApplicationTests`](../../../code/aircraftmanagement/src/test/java/isep/psoft/aisafe/AircraftManagementApplicationTests.java) | Context | The service starts with the shared configuration (security chain of `common` + `AircraftManagementAuthorizationRules`) |

**To implement** (proposed plan; the list follows the acceptance criteria above and the pattern of the other services)

| Test class | Type | What should be verified |
|---|---|---|
| `AircraftTest` | Unit (domain) | Constructor validation (blank registration/model, non-positive seats), default status `AVAILABLE`, `changeStatus`: allowed transitions, `IN_FLIGHT` → `UNDER_MAINTENANCE`/`INACTIVE` rejected, same status is a no-op |
| `AircraftModelTest` | Unit (domain) | Required fields; seats, fuel, range and speed must be positive |
| `AircraftStatusTest` | Unit | `parse` accepts any case, trims, rejects blank/unknown with the allowed values in the message |
| `PageDTOTest` | Unit | First/middle/last/out-of-range page, empty list, `totalPages` |
| `TopUtilizedModelsServiceTest`, `CalculateOperationalHoursServiceTest` | Unit (Mockito: `SearchAircraftsService`, `FlightRoutesClient`) | Sum per model, descending order, tie-break by name, exclusion of models with 0 minutes, 0 hours for aircraft without flights, propagation of `partial` |
| `UpdateAircraftStatusServiceTest` | Unit (Mockito) | Local update; not local → `forwardToOwner`; nothing found → `404`; unknown status → `400` before any call |
| `AircraftManagementAuthorizationRulesTest` | Slice (MockMvc + JWT) | `401` without token; `403` for the wrong role on each restricted endpoint (US101, US102, US105, US203, US206); allowed roles of B1/B3 |
| **`AircraftReplicaCollaborationTest`** | **Distributed** — two real replicas + a stub of Flight Routes (like `ReplicaCollaborationTest`) | Scenarios S1–S7: forwarding by id, merged and sorted search (S2), owner command and propagated `409` (S3), registration and model uniqueness across replicas, `400` for a model stored nowhere, peer headers (`401`/`400`), replica stopped → `X-Partial-Response`, B1a/B1b example JSON from the contract |

**Postman:** [postman/AircraftManagement.postman_collection.json](postman/AircraftManagement.postman_collection.json) + [environment](postman/AircraftManagement.postman_environment.json), against the three replicas — folders: setup, local access, forwarding, aggregation, commands (US101, US102, US105), security, integration with Flight Routes (US203, US204, US206, needs Flight Routes), replica failure (stop replica 2 before the last folder). Requests that create data use random values, so the collection can be run more than once.

## 6. Demo

From `code/`:

1. `./mvnw clean install`
2. `./scripts/start-replicas.sh aircraftmanagement` (Windows: `.\scripts\start-replicas.ps1 -Service aircraftmanagement`). Each replica loads different data (the `aisafe.bootstrap.*` properties; no `demo` profile is needed): replica 1 — models A320, B737 and aircraft CS-TUA, CS-TUB; replica 2 — model A330 and aircraft CS-TUC, CS-TUD; replica 3 — model E190 and aircraft CS-TUE, CS-TUF.
3. For US203, US204 and US206 also start Flight Routes: `.\scripts\start-replicas.ps1 -Service flightroutes -Profiles demo`.
4. `GET http://localhost:8081/actuator/health` (also 8181, 8281).
5. `POST http://localhost:8081/auth/dev-token` `{"username":"atcc1","roles":["ATCC"]}` (and `["BACKOFFICE_OPERATOR"]`, `["MAINTENANCE_TECHNICIAN"]`) → tokens for Postman / Swagger *Authorize*.
6. Run the Postman collection, or by hand:
   - `GET :8081/api/aircrafts` and `:8281/api/aircrafts` → the same 6 aircraft (`CS-TUA` … `CS-TUF`).
   - `GET :8081/api/aircrafts/CS-TUC` → `200`; log of replica 1: `Not found locally: forwarding GET ...`; replica 2: `AUDIT ... source=peer:aircraftmanagement-1` with the same correlation id.
   - `GET :8081/api/aircrafts?modelName=A330` → CS-TUC, CS-TUD (stored in replica 2); `?status=INACTIVE` → CS-TUF; `?manufacturingYear=2022` → CS-TUE, CS-TUF.
   - `POST :8081/api/aircrafts` with `modelName` `E190` (model stored in replica 3) → `201`; an unknown model → `400`; the registration `CS-TUC` → `409`.
   - `PATCH :8081/api/aircrafts/CS-TUC/status` `{"status":"UNDER_MAINTENANCE"}` → executed by replica 2; a US212 flight for CS-TUC in Flight Routes now gets `409` (aircraft not available). Set it back to `AVAILABLE`.
   - `PATCH …/CS-TUA/status` `{"status":"IN_FLIGHT"}` and then `{"status":"UNDER_MAINTENANCE"}` → `409` (transition rule). Set it back to `AVAILABLE`.
   - `GET :8081/api/aircrafts/operational-hours`, `…/CS-TUC/compatible-routes`, `GET :8081/api/aircraft-models/top-utilized?limit=5` (ATCC) → Flight Routes figures joined with the fleet.
   - Close the window of replica 2 → `GET :8081/api/aircrafts` returns 4 aircraft with `X-Partial-Response: true`; `GET …/CS-TUC` → `404`; `PATCH …/CS-TUC/status` → `503`.
   - HTTPS: add the `tls` profile (`instance1,tls`) and use `https://`.

H2 console: `http://localhost:8081/h2-console` (JDBC URL `jdbc:h2:mem:aircraftManagementdb-1`).

## 7. Observations

- Cross-replica uniqueness of the registration number and of the model name is best-effort (AP, see [L2 §3.3](../../2-containers/Containers.md#33-consistency-model-cap)): during a partition, or with two simultaneous registrations on different replicas, the same value can be stored twice. Deterministic ownership (hash of the registration → replica) would remove this at the cost of availability for writes when that replica is down.
- US203, US204 and US206 depend on Flight Routes being available (`503` otherwise); the figures are already summed over the Flight Routes replicas and a partial answer is passed on with `X-Partial-Response`.
- **US204 works for the Backoffice Operator and for the ATCC**: the JWT is forwarded to Flight Routes, whose rule for `GET /api/scheduled-flights/statistics/minutes-by-aircraft` (B4b) accepts `ATCC` and `BACKOFFICE_OPERATOR` (contract Q4); the rest of `/api/scheduled-flights/**` stays `ATCC` only, which matches US206. The Postman request *US204 Top 5 modelos mais utilizados (BACKOFFICE) -> 200* covers it.
- US205 depends on the answer to Q-AM1. US201 and US202 reuse the owner-forwarding pattern of S3 and need an `update` behaviour on `AircraftModel`.
- The PSOFT requirement of HATEOAS links is not implemented yet (see O1 in L3).
