# The `/api/v2/unicorns` mock (L2)

SIDIS Assignment 2 asks for a mock endpoint `/api/v2/unicorns` that returns the "mood" of the airplanes in JSON, and for an explanation of how it fits CQRS. This page is that explanation, plus how to create and run the mock.

Files: [`5-scenarios/unicorns/postman/UnicornsMock.postman_collection.json`](../5-scenarios/unicorns/postman/UnicornsMock.postman_collection.json) (collection "AISafe - Unicorns Mock", one request with a saved example and tests) and [`UnicornsMock.postman_environment.json`](../5-scenarios/unicorns/postman/UnicornsMock.postman_environment.json) (variable `unicornsMockUrl`).

## 1. Contract

`GET {{unicornsMockUrl}}/api/v2/unicorns` → `200`, `Content-Type: application/json`, a JSON **array** with one item per aircraft:

| Field | Type | Meaning |
|---|---|---|
| `registration` | string | Aircraft registration (identity in Aircraft Management), e.g. `CS-TUA` |
| `model` | string | Aircraft model name |
| `operationalStatus` | string | `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE` or `INACTIVE` (same values as Aircraft Management) |
| `mood` | string | Derived from `operationalStatus` (rule below) |
| `timestamp` | ISO-8601 instant | When the read model last applied a status event for this aircraft |
| `source` | string | Always `"read-model"`: the answer comes from the query side |

Mood rule (a pure function of the status, computed when the event is projected):

| `operationalStatus` | `mood` |
|---|---|
| `AVAILABLE` | `HAPPY` |
| `IN_FLIGHT` | `BUSY` |
| `UNDER_MAINTENANCE` | `GROUNDED_AND_GRUMPY` |
| anything else | `NEUTRAL` |

```json
[
  { "registration": "CS-TUA", "model": "A320", "operationalStatus": "AVAILABLE",
    "mood": "HAPPY", "timestamp": "2026-11-18T09:15:00Z", "source": "read-model" },
  { "registration": "CS-TUC", "model": "E195", "operationalStatus": "UNDER_MAINTENANCE",
    "mood": "GROUNDED_AND_GRUMPY", "timestamp": "2026-11-17T22:47:10Z", "source": "read-model" }
]
```

The request's tests check: only `GET` is used, status `200`, JSON content type, the body is a non-empty array, every item has `registration` and `mood`, every mood follows the rule, and every item has `source = "read-model"` and a valid `timestamp`.

## 2. The `/api/v2/unicorns` mock and CQRS

- **It is a pure query (read side).** The endpoint only reads. In the CQRS design of AISafe it would be served by a **projection**: a table such as `AIRCRAFT_MOOD(registration, model, operationalStatus, mood, timestamp)`, kept by any service that subscribes to Aircraft Management's `AircraftRegistered` and `AircraftStatusChanged` events (see [Integration-Contracts §9.4](Integration-Contracts.md#94-events-expected-from-the-other-services)). Each event upserts one row and recomputes `mood`. A `GET` reads that table and nothing else.
- **It never touches the write model.** The aircraft aggregate lives in Aircraft Management's own database, and only its commands (register, change status) change it. The mood projection is never written by a client and never queried by the write side. This is the same split Flight Routes uses: commands on the owner's `FlightRoute`/`ScheduledFlight`, queries on `REPLICATED_ROUTE`/`REPLICATED_SCHEDULED_FLIGHT`.
- **Why a mock is legitimate here.** What consumers depend on is the **read-model contract** (the JSON above), not on how or where writes happen. Because the query side is independent of the command side, the contract can be stubbed by a Postman mock server and used by front-ends, tests and other teams before the projection exists. Swapping the mock for the real projection does not change any consumer, as long as the contract is kept.
- **It shows query-side independence.** The mock answers even when Aircraft Management (the writer) is down. A real projection would behave the same way: it keeps answering from its own copy.
- **It shows eventual consistency.** `timestamp` is when the read model *applied* the last status event, not when the aircraft changed. Between a status change in Aircraft Management and the arrival of its event, the mood may be stale (typically milliseconds; longer if the consumer is down, since the event waits in its durable queue). Clients that need the authoritative status must ask the write side (`GET /api/aircrafts/{registration}`).

## 3. Create and run the mock server (Postman app)

1. Open Postman. **Import** (top left) → select `docs/5-scenarios/unicorns/postman/UnicornsMock.postman_collection.json` and `UnicornsMock.postman_environment.json`.
2. In the sidebar, open the **AISafe - Unicorns Mock** collection → `...` (View more actions) → **Mock collection**.
3. Name it `aisafe-unicorns-mock`. Leave the environment empty (the mock does not need it) and choose **public** or private access. Click **Create Mock Server**.
4. Copy the mock server URL Postman shows (`https://<id>.mock.pstmn.io`).
5. Open **Environments** → **AISafe Unicorns Mock** → paste the URL into the current value of `unicornsMockUrl` (no trailing `/`). Save.
6. Select the **AISafe Unicorns Mock** environment (top right), open `GET /api/v2/unicorns - aircraft moods (read model)` and click **Send**. The mock returns the saved example `200 - aircraft moods`; the **Test Results** tab shows 7 passed tests.
7. Optional, from the command line: export the environment with the real URL, then
   `npx newman run docs/5-scenarios/unicorns/postman/UnicornsMock.postman_collection.json -e <exported-environment>.json`
   (or pass `--env-var unicornsMockUrl=https://<id>.mock.pstmn.io`).

A private mock server also needs the header `x-api-key: <your Postman API key>`. Add it to the request if you chose private access.

To change what the mock returns, edit the saved example (`200 - aircraft moods`) in the collection. Postman mock servers answer with the example whose method and path match the request.
