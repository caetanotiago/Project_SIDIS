# C4+1 — Maintenance Service: Scenarios

> Use cases and end-to-end behaviour that validate L2–L4. Reference: [Flight Routes — Scenarios](../flightroutes/FlightRoutes-Scenarios.md).

## 1. User stories

| WP | US | Description | Actor |
|---|---|---|---|
| | | | |

## 2. Acceptance criteria

Include the distribution criteria (any replica answers with the data of all replicas; partial responses; updates by the owner replica; `401` for peer requests without service key; `503` when no replica of a remote service answers).

## 3. System sequence diagram

![Maintenance-SSD](svg/Maintenance-SSD.svg)

## 4. Scenarios (sequence diagrams)

Include at least one **peer-forwarding** scenario and one **aggregation with a failed replica** scenario.

## 5. Tests

Unit tests, a distributed test with two replicas (like `ReplicaCollaborationTest`), Postman collection in `postman/`.

## 6. Demo

## 7. Observations
