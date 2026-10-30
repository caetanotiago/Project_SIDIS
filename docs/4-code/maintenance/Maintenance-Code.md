# C4 Level 4 — Maintenance Service: Code

> Detalhe das classes relevantes do [contentor Maintenance](../../3-components/maintenance/Maintenance-Components.md).

Este documento detalha o modelo de domínio e os contratos REST do serviço **Maintenance**, incluindo a distribuição de responsabilidades entre o domínio, a API e a camada de aplicação.

## 1. Domain Model

![Maintenance-DM](svg/Maintenance-DM.svg)

| Classe | Papel DDD | Atributos / responsabilidade |
|---|---|---|
| `Maintenance` | Aggregate Root | `id` (`UUID`): identificador da intervenção; `aircraftRegistration` (`String`): matrícula associada; `scheduledDate` (`LocalDate`): data prevista; `completionDate` (`LocalDate`, opcional): data de conclusão; `serviceType` (`String`): tipo de serviço; `report` (`MaintenanceReport`, opcional): dados do trabalho concluído; `status` (`MaintenanceStatus`): estado atual. Mantém as invariantes e controla as transições de estado da intervenção. |
| `MaintenanceStatus` | Enum | Estados possíveis: `SCHEDULED`, `IN_PROGRESS`, `COMPLETED` e `CANCELLED`. |
| `MaintenanceReport` | Value Object | Dados de conclusão da intervenção, incluindo a descrição dos trabalhos realizados. É associado à manutenção quando esta é concluída. |

O `MaintenanceRecordService` orquestra as operações e a lógica transacional. As regras e transições próprias da intervenção são mantidas no aggregate `Maintenance`.

## 2. Class Diagram

![Maintenance-CD](svg/Maintenance-CD.svg)

## 3. REST API

Os seguintes contratos serão expostos pelo `MaintenanceController`:

| Operação | Método e endpoint | Pedido | Resposta |
|---|---|---|---|
| Agendar manutenção | `POST /api/maintenance` | Matrícula (`aircraftRegistration`), data prevista (`scheduledDate`) e tipo de serviço (`serviceType`). | `201 Created` com os dados da manutenção criada, incluindo o identificador e o estado `SCHEDULED`. |
| Concluir intervenção | `PATCH /api/maintenance/{id}/complete` | Data de conclusão (`completionDate`) e objeto `report` com o texto (`text`) dos trabalhos realizados. | `200 OK` com os dados atualizados da manutenção e o estado `COMPLETED`. |
| Consultar manutenção por ID | `GET /api/maintenance/{id}` | Identificador da manutenção no caminho. | `200 OK` com a manutenção local ou encontrada numa réplica peer; `404 Not Found` se não existir em nenhuma réplica. |
| Consultar todas as manutenções | `GET /api/maintenance` | Sem parâmetros. | `200 OK` com os registos locais e das réplicas disponíveis, ordenados da data mais recente para a mais antiga. |
| Consultar histórico | `GET /api/maintenance/aircraft/{registration}` | Matrícula da aeronave no caminho. | `200 OK` com a lista de manutenções da matrícula, ordenada cronologicamente pela data prevista (mais recente primeiro). |

## 4. Responsabilidades e transações

O `MaintenanceController` trata da interface HTTP e delega cada operação ao `MaintenanceRecordService`. O serviço de aplicação orquestra a lógica de negócio, as validações necessárias e as operações transacionais de persistência através do `MaintenanceRepository`. O `AircraftClient` usa os contratos B3 de HTTP/REST; se a gravação do registo iniciado falhar após a alteração da aeronave para `UNDER_MAINTENANCE`, o serviço tenta compensar o estado para `AVAILABLE`. Os GETs agregam resultados peer e o PATCH de conclusão é encaminhado para a réplica proprietária quando o registo não é local.
