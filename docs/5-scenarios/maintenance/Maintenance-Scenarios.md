# C4+1 — Maintenance Service: Scenarios

> The "+1" view: real use cases and end-to-end behaviour across containers and components. Each scenario validates [L2](../../2-containers/Containers.md), [L3](../../3-components/maintenance/Maintenance-Components.md) and [L4](../../4-code/maintenance/Maintenance-Code.md).

## 1. User stories

The use cases are the WP#4 user stories of the PSOFT assignment (the monolith that this service replaces), now served by three replicas.

| WP | US | Description | Actor |
|---|---|---|---|
| 0A | — | Preload maintenance templates (bootstrap of initial data) | — |
| 4A | US115 (templates) | Create a maintenance template: name, type (inspection, scheduled maintenance, overhaul, modification), applicable aircraft models and checklist (+ interval, for US222) | Maintenance Technician |
| 4A | US115 (records) | Create a maintenance record for an aircraft: registration, maintenance type (template), description, start date, expected duration and its checklist (defined by the template) | Maintenance Technician |
| 4A | US116 | View all maintenance records of a specific aircraft | Maintenance Technician (also Supervisor) |
| 4A | US117 | View the total number of maintenance hours of the aircraft of the fleet | ATCC |
| 4A | US119 | Mark a maintenance record as completed and add completion notes | Maintenance Technician |
| 4B | US217 | Categorise maintenance records by component (engine, airframe, avionics, interior, exterior) | Maintenance Technician |
| 4B | US218 | Search maintenance records by aircraft, date range or component | Maintenance Technician (also Supervisor) |
| 4B | US219 | View all ongoing maintenance activities across the fleet | Maintenance Supervisor |
| 4B | US220 | Generate reports on maintenance costs per aircraft or per aircraft model | ATCC |
| 4B | US221 | View the average maintenance turnaround time per aircraft type | Maintenance Supervisor |
| 4B | US222 | Receive alerts when aircraft are due for scheduled maintenance based on flight hours or calendar days | ATCC |

### Clarifications (PSOFT forum)

- **Conv. 009:** "aircraft type" (US221) is the aircraft model.
- **Conv. 010:** the *fleet* is the set of all aircraft managed in AISafe.
- **Conv. 016:** the maintenance interval (flight hours and/or calendar days) is an attribute of the template; the due-maintenance check is **triggered manually** by the user.
- **Operational constraint:** "aircraft under maintenance cannot be assigned to flights" — realised through the aircraft status in Aircraft Management (B3b), which Flight Routes already checks (B1a).
- **Group decisions** (Integration-Contracts §7): after maintenance the aircraft returns to `AVAILABLE` (Q1); B3b is called after the local write was flushed and compensated if the local transaction rolls back (Q7).

## 2. Acceptance criteria

**Templates:** AC1 name, type, at least one applicable model and one checklist item required (else `400`); AC2 every applicable model exists in Aircraft Management (else `400`); AC3 unique name in every replica that answers (else `400`); AC4 interval values positive; no interval = not periodic; AC5 the standard templates exist in every replica at start-up (WP#0A).

**Records:** AC6 the aircraft exists (else `404`) and the template exists in some replica (else `404`); AC7 the template applies to the aircraft's model (else `400`); AC8 start date not in the future, expected duration > 0, component one of the five values (else `400`); AC9 created `IN_PROGRESS` with the template's checklist, `201` + `Location` + links; AC10 the aircraft becomes `UNDER_MAINTENANCE`; an aircraft in flight cannot (`409`); if Aircraft Management is unavailable nothing is created (`503`).

**Completion and categorisation:** AC11 only `IN_PROGRESS` records can be completed or categorised (else `409`); AC12 notes required, cost ≥ 0, actual duration > 0, completion date between the start date and today (else `400`); AC13 the aircraft returns to `AVAILABLE` only if no other record of it is ongoing in any replica (and every replica answered); otherwise it stays `UNDER_MAINTENANCE` and the response carries `X-Aircraft-Released: false`; AC14 concurrent updates → `409`.

**Queries and reports:** AC15 US116 for an unknown aircraft → `404` (not an empty history); AC16 lists sorted by start date (most recent first) and paginated (`size` 1–100) after merging; AC17 US218 filters combine with AND, `from` ≤ `to`; AC18 US117 counts actual hours of completed records and expected hours of ongoing ones; AC19 US220 only completed records, `groupBy` `AIRCRAFT` or `MODEL` (else `400`); AC20 US221 average = total actual minutes / number of completed records per model, computed after adding the replicas; AC21 US222 lists every aircraft (not `INACTIVE`) × applicable periodic template whose flight-hours or calendar-days limit is reached, without an ongoing record of that template; without flight hours (Flight Routes down) the calendar-day alerts are still returned, flagged partial.

**Distribution:** AC22 any replica answers with the data of all replicas, `404` only if no replica has it; AC23 a failed peer gives a `200` with `X-Partial-Response: true`; AC24 a record is only modified by its owner replica; AC25 peer requests without a valid `X-Service-Key` → `401`, `X-Peer-Hops > 1` → `400`; AC26 JWT + roles on every endpoint (`401` / `403`), every request audited; AC27 `503` when no replica of Aircraft Management answers.

## 3. System sequence diagram

"System" = any of the three replicas.

![Maintenance-SSD](svg/Maintenance-SSD.svg)

## 4. Scenarios (sequence diagrams)

### 4.1 Distributed scenarios

**S1 — Peer-to-peer query resolution: GET a record stored in another replica (same for templates and the template lookup of US115)**

![Maintenance-SD-PeerForwarding](svg/Maintenance-SD-PeerForwarding.svg)

**S2 — Response aggregation with a failed replica (US219; same for US116, US218 and the template list)**

![Maintenance-SD-PeerAggregation](svg/Maintenance-SD-PeerAggregation.svg)

**S3 — Complete a record: command forwarded to the owner replica, aircraft released (US119; same forwarding for US217)**

![Maintenance-SD-CompleteRecord](svg/Maintenance-SD-CompleteRecord.svg)

**S4 — Statistics aggregated with sums and counts (US221; same pattern for US117 and US220)**

![Maintenance-SD-Turnaround](svg/Maintenance-SD-Turnaround.svg)

### 4.2 Use-case scenarios

**S5 — Create a maintenance record (US115) — cross-service consistency with Aircraft Management**

![Maintenance-SD-CreateRecord](svg/Maintenance-SD-CreateRecord.svg)

**S6 — Maintenance alerts (US222)**

![Maintenance-SD-Alerts](svg/Maintenance-SD-Alerts.svg)

## 5. Tests

From `code/`: `./mvnw -pl maintenance -am test` — 45 tests in the maintenance module (plus the 20 of `common`), all passing.

| Test class | Type | What is verified |
|---|---|---|
| `MaintenanceTemplateTest` | Unit (domain) | required data and normalisation, interval rules, `appliesTo`, `isPeriodic`, same id for reference templates, enum parsing with `400` |
| `MaintenanceRecordTest` | Unit (domain) | created `IN_PROGRESS` with checklist and template snapshot, start date rules, completion rules (once, dates, cost, duration), categorisation only while in progress, `maintenanceMinutes` |
| `MaintenanceDuePolicyTest` | Unit (domain) | flight-hours and calendar-days criteria, never performed, hours not recorded at completion, non-periodic templates |
| `CreateMaintenanceRecordServiceTest` | Unit (mocks) | order B3a → template → save/flush → B3b; nothing saved for unknown aircraft/template, template not applicable, invalid component; B3b failure propagates (rollback) |
| `CompleteMaintenanceRecordServiceTest` | Unit (mocks) | local completion + release, forwarding to the owner, owner does not touch the aircraft on a peer request, `404` / `409` |
| `AircraftAvailabilityServiceTest` | Unit (mocks) | release only without other ongoing records and with every replica answering; failed release does not fail the completion |
| `MaintenanceStatisticsServiceTest` | Unit (mocks) | partial results added correctly: turnaround average after the sum (400, not 375), costs per model, hours, latest completion + ongoing flag |
| `MaintenanceAlertServiceTest` | Unit (mocks) | applicable templates only, `INACTIVE` and ongoing skipped, calendar-only alerts flagged partial without flight hours |
| `MaintenanceApplicationTests` | Context | the service starts with the real security configuration of `common` |
| **`MaintenanceReplicaCollaborationTest`** | **Distributed** — two real replicas + a stateful stub of Aircraft Management | scenarios below |

`MaintenanceReplicaCollaborationTest`:

1. Templates: reference templates listed once; template created on B found from A (S1); duplicate name and unknown model → `400`.
2. Records stored where created, the aircraft goes `UNDER_MAINTENANCE` in the stub; aircraft in flight → `409` (nothing stored); unknown aircraft → `404`; template not applicable / future start date → `400`.
3. GET on A of a record of B → forwarded, links rebuilt for A; US219 from A and B → same list; US116 paginated after merging, unknown aircraft → `404`; US218 filters (S2).
4. US217 and US119 on A for a record of B → executed by B; completing again → owner's `409` propagated (S3).
5. The aircraft is released only when its last ongoing record is completed (`X-Aircraft-Released`).
6. US117/US220/US221 add the records of both replicas (S4).
7. US222 with the stub's fleet and flight hours → expected alerts; `INACTIVE` aircraft ignored (S6).
8. Peer headers without/wrong service key → `401`; hops 2 → `400`; valid peer request → local data only; no token → `401`; wrong role → `403`.
9. Replica B stopped: A answers `200` with `X-Partial-Response: true`; record only in B → `404`; a completion on A keeps the aircraft `UNDER_MAINTENANCE` (B could not confirm).

**Postman:** [postman/Maintenance.postman_collection.json](postman/Maintenance.postman_collection.json) + [environment](postman/Maintenance.postman_environment.json), against the three Maintenance replicas with the `demo` profile and the real Aircraft Management (and Flight Routes for flight hours). Folders: 0 setup (tokens), 1 templates, 2 records, 3 local access, 4 forwarding, 5 aggregation, 6 command forwarding, 7 statistics/reports/alerts, 8 security, 9 replica failure (stop replica 2 before folder 9). Verified with Newman: folders 0–8 — 48 requests, 74 assertions, 0 failures; folder 9 — 4 requests, 6 assertions, 0 failures. The collection can be run again without restarting (template names are unique per run and every record created is completed).

## 6. Demo

From `code/`:

1. `./mvnw clean install`
2. Start Aircraft Management (its demo data is loaded by the instance profiles): `./scripts/start-replicas.sh aircraftmanagement` — aircraft CS-TUA (A320), CS-TUB (B737, `UNDER_MAINTENANCE`), CS-TUC/CS-TUD (A330), CS-TUE/CS-TUF (E190). Optionally `./scripts/start-replicas.sh flightroutes demo` (flight hours used by US222).
3. `./scripts/start-replicas.sh maintenance demo` (Windows: `.\scripts\start-replicas.ps1 -Service maintenance -Profiles demo`). Every replica has the reference templates; each replica loads **different** records.
4. Tokens: `POST http://localhost:8082/auth/dev-token` with `{"username":"tech1","roles":["MAINTENANCE_TECHNICIAN"]}`, `{"username":"sup1","roles":["MAINTENANCE_SUPERVISOR"]}`, `{"username":"atcc1","roles":["ATCC"]}`.
5. Run the Postman collection, or by hand:
   - `GET :8082/api/maintenance-records/ongoing` and `:8182/...` → the same list (supervisor).
   - `GET :8082/api/maintenance-records/{id of a record of replica 3}` → `200`; log of replica 1: `Not found locally: forwarding GET ...`; replica 3: `AUDIT ... source=peer:maintenance-1` with the same correlation id.
   - `POST :8182/api/maintenance-records` for CS-TUA → `GET :8081/api/aircrafts/CS-TUA` shows `UNDER_MAINTENANCE`; `PATCH :8082/api/maintenance-records/{id}/complete` → executed by replica 2 (log of replica 1: `forwarding PATCH ... to the owner replica`), response `X-Aircraft-Released: true`, CS-TUA back to `AVAILABLE`.
   - `GET :8282/api/maintenance-records/reports/turnaround` (supervisor) and `/reports/costs?groupBy=MODEL` (ATCC) → figures of the three replicas.
   - `GET :8082/api/maintenance-alerts` (ATCC) → aircraft due for maintenance.
   - Stop replica 2 → `ongoing` returns the records of replicas 1 and 3 with `X-Partial-Response: true`; `/actuator/health` shows `8182` `OPEN`.
   - HTTPS: add the `tls` profile (`demo,tls`) and use `https://`.

H2 console: `http://localhost:8082/h2-console` (JDBC URL `jdbc:h2:mem:maintenancedb-1`).

## 7. Observations

- Cross-replica uniqueness of template names is best-effort (AP, see [L2 §3.3](../../2-containers/Containers.md#33-consistency-model-cap)).
- Two records of the same aircraft completed at the same instant on two replicas may each see the other as ongoing, so neither releases the aircraft: it stays `UNDER_MAINTENANCE` (the safe side) until the ATCC changes it (US105).
- US222 alerts are computed on demand (manual trigger) and not stored; a scheduled check and notification channel are future work.
- Without Flight Routes, Aircraft Management cannot give flight hours (US206): US222 then returns only calendar-day alerts, flagged partial.
- Demo records are inserted directly in each replica (without calling Aircraft Management); their aircraft statuses match the Aircraft Management demo data (CS-TUB `UNDER_MAINTENANCE` with an ongoing C-Check).
- Flight hours come from scheduled flights (Flight Routes, through Aircraft Management US206), not from actual flown hours; "flight hours at completion" are entered by the technician.
