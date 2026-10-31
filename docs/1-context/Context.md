# C4 Level 1 — System Context

> *"How does this system fit into the world?"* — the big picture, for every audience. Only people and software systems; no technology.

![C4-L1-SystemContext](svg/C4-L1-SystemContext.svg)

## The system

**AISafe Flight Management System** supports the daily operation of an airline: its fleet, the airports it operates in, the routes it flies, the flights it schedules and the maintenance of its aircraft.

It was originally a monolith (PSOFT). In SIDIS it is redesigned as a distributed system that keeps working when parts of it fail; how it is split is shown in [Level 2](../2-containers/Containers.md).

## People (users)

| Person | What they do with AISafe |
|---|---|
| **Administrator** | Would manage users and their roles. The role exists, but user management is not part of Assignment 1: no AISafe function uses it yet. |
| **Backoffice Operator** | Registers and updates airports (including their status and certified aircraft models) and aircraft models; updates and deactivates routes; consults statistics such as the busiest airports and the most utilised aircraft models. |
| **Air Traffic Control Coordinator (ATCC)** | Registers aircraft and changes their status; looks up and searches airports; defines flight routes, assigns aircraft to routes at a date/time, and queries the route network (popular routes, network distance, alternative itineraries, routes compatible with an aircraft, operational hours); follows maintenance hours and costs of the fleet and checks which aircraft are due for maintenance. |
| **Maintenance Technician / Supervisor** | Defines maintenance templates, records and completes the maintenance of the aircraft (taking them out of service while it lasts), and follows ongoing maintenance and turnaround times. |

Every person authenticates and is given a **role**; each role can only use the functions meant for it.

## External systems

AISafe does not depend on external software systems in this assignment. Users are identified with tokens issued for the AISafe roles (in development, by a test endpoint; a real identity provider is future work).

## Main relationships

| From | To | Purpose |
|---|---|---|
| Backoffice Operator | AISafe | Register airports and aircraft models, manage routes, read statistics |
| ATCC | AISafe | Register aircraft, create routes, schedule flights, query airports and the network |
| Maintenance Technician / Supervisor | AISafe | Record maintenance, check aircraft status |

## Quality goals (from the assignment)

| Goal | Meaning for AISafe |
|---|---|
| **Availability and fault tolerance** | The system keeps answering when a server or part of the network fails. |
| **Scalability** | More servers can be added to share the load. |
| **Security** | Fleet, route and maintenance data is operationally sensitive: only authorised people see it, it travels encrypted, and every access is recorded. |
