# AISafe — Architecture Documentation (C4+1)

The architecture is documented with the **C4 model** (Simon Brown) plus a fifth view of **scenarios** (C4+1). Each level zooms into the previous one and is written for a different audience.

| Level | Question it answers | Audience | Document |
|---|---|---|---|
| **L1 — Context** | How does AISafe fit into the world? Who uses it? | everyone (business, managers, team) | [1-context/Context.md](1-context/Context.md) |
| **L2 — Containers** | Which separately deployable units make up AISafe, and how do they communicate? Replication, consistency, fault tolerance, security, deployment. | engineers, project manager | [2-containers/Containers.md](2-containers/Containers.md) · [Ports.md](2-containers/Ports.md) |
| **L3 — Components** | What are the main building blocks inside each container? | developers of that service | [3-components/](3-components/) |
| **L4 — Code** | Which classes implement the components (domain model, class diagrams, patterns)? | developers | [4-code/](4-code/) |
| **+1 — Scenarios** | How do the elements work together in each use case (user stories, sequence diagrams, tests)? | everyone; validates L1–L4 | [5-scenarios/](5-scenarios/) |

## Contents per container

| Container | Owner | L3 Components | L4 Code | +1 Scenarios |
|---|---|---|---|---|
| Aircraft Management (WP#1) | TBD | [Components](3-components/aircraftmanagement/AircraftManagement-Components.md) | [Code](4-code/aircraftmanagement/AircraftManagement-Code.md) | [Scenarios](5-scenarios/aircraftmanagement/AircraftManagement-Scenarios.md) |
| Maintenance (WP#4) | TBD | [Components](3-components/maintenance/Maintenance-Components.md) | [Code](4-code/maintenance/Maintenance-Code.md) | [Scenarios](5-scenarios/maintenance/Maintenance-Scenarios.md) |
| Airports (WP#2) | TBD | [Components](3-components/airports/Airports-Components.md) | [Code](4-code/airports/Airports-Code.md) | [Scenarios](5-scenarios/airports/Airports-Scenarios.md) |
| **Flight Routes (WP#3)** | complete | [Components](3-components/flightroutes/FlightRoutes-Components.md) | [Code](4-code/flightroutes/FlightRoutes-Code.md) | [Scenarios](5-scenarios/flightroutes/FlightRoutes-Scenarios.md) |
| *common library* (shared) | — | [Components](3-components/common/Common-Components.md) | [Code](4-code/common/Common-Code.md) | (exercised by every scenario) |

L1 and L2 describe the **whole system** and are shared by the team; L3, L4 and +1 are written **per container** by its owner. Flight Routes is the reference example.

## Folder layout and conventions

```text
docs/
├── README.md                     this index
├── 1-context/                    L1 — Context.md + puml/ + svg/
├── 2-containers/                 L2 — Containers.md, Ports.md + puml/ + svg/
├── 3-components/<container>/     L3 — <Container>-Components.md + puml/ + svg/
├── 4-code/<container>/           L4 — <Container>-Code.md + puml/ + svg/
├── 5-scenarios/<container>/      +1 — <Container>-Scenarios.md + puml/ + svg/ + postman/
└── _template/                    templates for L3, L4 and +1
```

- **Notation.** L1–L3 use the [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML) library (`!include <C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>`, `<C4/C4_Deployment>`), so persons, systems, containers and components look the same in every diagram. L4 and +1 use UML (class and sequence diagrams).
- **Diagrams as code.** PlantUML sources in `puml/`, rendered SVGs in `svg/`, both committed. To render (from the folder of the level): `java -jar plantuml.jar -tsvg -o ../svg puml/*.puml`. No Graphviz is needed (`!pragma layout smetana` in UML diagrams). The VS Code *PlantUML* extension previews them.
- **Keep diagrams in sync with the code.** L3 components and L4 classes use the real class/package names and link to the source files; update them in the same commit as the code.
- **Language:** English for documentation, consistent naming across levels.
