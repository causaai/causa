# Alert Module Architecture

## Overview

The Alert Module is responsible for ingesting, processing, filtering, and storing memory/GC alerts from Prometheus Alertmanager. It implements a hexagonal architecture pattern to maintain clean separation between business logic and external dependencies.

**Version**: 0.0.1  
**Last Updated**: 2026-06-11

---

## Table of Contents

1. [Module Purpose](#module-purpose)
2. [Architecture Overview](#architecture-overview)
3. [Component Breakdown](#component-breakdown)
4. [Data Flow](#data-flow)
5. [Design Decisions & Reasoning](#design-decisions--reasoning)
6. [Configuration](#configuration)
7. [Integration Points](#integration-points)
8. [Error Handling](#error-handling)
9. [Performance Considerations](#performance-considerations)
10. [Future Enhancements](#future-enhancements)

---

## Module Purpose

### Primary Responsibilities

1. **Alert Ingestion**: Receive webhook notifications from Prometheus Alertmanager
2. **Alert Filtering**: Apply severity, namespace, and cooldown filters to reduce noise
3. **Alert Persistence**: Store accepted alerts in PostgreSQL with pgvector extension
4. **Alert Querying**: Provide REST API for historical alert retrieval
5. **Diagnostic Triggering**: Initiate LLM-based diagnostic pipeline for accepted alerts

### Key Features

- ✅ Alertmanager v4 webhook compatibility
- ✅ Configurable severity filtering (critical, warning, info)
- ✅ Namespace-based alert exclusion
- ✅ Cooldown mechanism to prevent alert spam
- ✅ Deterministic alert ID generation
- ✅ Historical alert queries by container or globally
- ✅ Hexagonal architecture for testability and maintainability

---

## Architecture Overview

### Hexagonal Architecture Pattern

The Alert Module follows the **Ports and Adapters** pattern (Hexagonal Architecture):

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRIMARY ADAPTERS                           │
│  (How the outside world interacts with the module)              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐              │
│  │ AlertWebhook     │         │ AlertQuery       │              │
│  │ Controller       │         │ Controller       │              │
│  │ (POST webhook)   │         │ (GET queries)    │              │
│  └────────┬─────────┘         └────────┬─────────┘              │
│           │                            │                        │
│           │ ┌────────────────────────┐ │                        │
│           └─┤   API Layer (DTOs,     ├─┘                        │
│             │   Validators, Mappers) │                          │
│             └───────────┬────────────┘                          │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼──────────────────────────────────────┐
│                      CORE DOMAIN                               │
│  (Business logic - framework agnostic)                         │
├─────────────────────────┼──────────────────────────────────────┤
│                         │                                      │
│  ┌──────────────────────▼─────────────────┐                    │
│  │      AlertService (Primary Port)       │                    │
│  │  • processAlerts(List<Alert>)          │                    │
│  │  • isInCooldown(Alert)                 │                    │
│  │  • Severity filtering                  │                    │
│  │  • Namespace filtering                 │                    │
│  │  • Cooldown deduplication              │                    │
│  └──────────────┬──────────────────────────┘                   │
│                 │                                              │
│  ┌──────────────▼──────────────────────────┐                   │
│  │     Alert Domain Model                  │                   │
│  │  • alertId: String                      │                   │
│  │  • timestamp: Instant                   │                   │
│  │  • severity: AlertSeverity (enum)       │                   │
│  │  • status: AlertStatus (enum)           │                   │
│  │  • generateAlertId(container, ts)       │                   │
│  │  • getCooldownKey()                     │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                │
│  ┌─────────────────────────────────────────┐                   │
│  │  AlertRepository (Secondary Port)       │                   │
│  │  • save(Alert)                          │                   │
│  │  • findById(String)                     │                   │
│  │  • findByContainerName(String)          │                   │
│  │  • findAll()                            │                   │
│  └─────────────────────────────────────────┘                   │
└─────────────────────────┬──────────────────────────────────────┘
                          │
┌─────────────────────────┼──────────────────────────────────────┐
│                   SECONDARY ADAPTERS                           │
│  (How the module interacts with external systems)              │
├─────────────────────────┼──────────────────────────────────────┤
│                         │                                      │
│  ┌──────────────────────▼─────────────────┐                    │
│  │  AlertRepositoryImpl                   │                    │
│  │  (JPA/Hibernate implementation)        │                    │
│  │  • Uses AlertEntity                    │                    │
│  │  • Uses AlertEntityMapper              │                    │
│  │  • Implements AlertRepository port     │                    │
│  └──────────────┬──────────────────────────┘                   │
│                 │                                              │
│  ┌──────────────▼──────────────────────────┐                   │
│  │      PostgreSQL + pgvector              │                   │
│  │  • alerts table                         │                   │
│  │  • Flyway migrations                    │                   │
│  └─────────────────────────────────────────┘                   │
└────────────────────────────────────────────────────────────────┘
```

---

## Component Breakdown

### 1. API Layer (Primary Adapters)

#### Controllers

**AlertWebhookController**
```
Location: com.causa.api.controllers.AlertWebhookController
Endpoint: POST /api/v1/webhooks/alerts
Purpose: Receives Alertmanager webhook notifications
```

- Validates incoming webhook payload
- Maps DTO to domain model
- Delegates to AlertService for processing
- Triggers diagnostic pipeline for accepted alerts
- Returns structured response (accepted/partial/rejected)

**AlertQueryController**
```
Location: com.causa.api.controllers.AlertQueryController
Endpoints: 
  - GET /api/v1/alerts
  - GET /api/v1/containers/{containerName}/alerts
Purpose: Provides read-only access to historical alerts
```

- Fetches alerts from repository
- Maps domain models to response DTOs
- Validates path parameters
- Returns paginated results

**DiagnosticQueryController**
```
Location: com.causa.api.controllers.DiagnosticQueryController
Endpoint: GET /api/v1/alerts/{alertId}/diagnostics
Purpose: Retrieves LLM diagnostic analysis for specific alerts
```

#### DTOs

**Request DTOs**
- `AlertWebhookRequest`: Models Alertmanager v4 webhook payload
  - Matches official Prometheus schema
  - Contains: version, status, receiver, groupLabels, commonLabels, alerts array
  - Uses `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility

**Response DTOs**
- `AlertResponse`: Webhook ingestion response (accepted/partial/rejected)
- `AlertListResponse`: Wrapper for alert query results
- `AlertDetailsResponse`: Single alert representation for queries
- `DiagnosticDetailsResponse`: LLM diagnostic analysis response
- `ErrorResponse`: Standardized error structure

#### Validators

**AlertWebhookValidator**
```java
Purpose: Validates Prometheus Alertmanager webhook payloads
Checks:
  ✓ Request body not null
  ✓ Alerts array not null/empty
  ✓ Alertmanager version matches expected (v4)
  ✓ Each alert has required fields (status, labels, alertname)
Returns: List<String> of validation errors (empty = valid)
```

**PathParamValidator**
```java
Purpose: Validates REST API path parameters
Validates:
  ✓ Alert ID format: {containerName}-{epochMillis}
  ✓ Container name format: alphanumeric, dots, hyphens, underscores
  ✓ Length constraints (max 255 chars for containers, 512 for alert IDs)
  ✓ Regex pattern matching
Returns: List<String> of validation errors
```

#### Mappers

**AlertMapper** (Webhook → Domain)
```java
Purpose: Maps Alertmanager webhook DTO to Alert domain model
Responsibilities:
  • Extracts labels (alertname, severity, namespace, pod, container)
  • Parses ISO 8601 timestamps
  • Generates deterministic alert IDs
  • Handles label fallback (labels → annotations)
  • Sanitizes null/blank values with defaults
```

**AlertResponseMapper** (Domain → Query Response)
```java
Purpose: Maps Alert domain model to AlertDetailsResponse DTO
Responsibilities:
  • Converts AlertSeverity enum to string
  • Converts AlertStatus enum to string
  • Preserves all alert metadata
  • Supports batch conversion (List<Alert> → List<AlertDetailsResponse>)
```

**DiagnosticResponseMapper** (Domain → Query Response)
```java
Purpose: Maps Diagnostic domain model to DiagnosticDetailsResponse DTO
```

#### Exception Handling

**AlertExceptionHandler**
```
Location: com.causa.api.exceptions.AlertExceptionHandler
Annotated: @ServerExceptionMapper (Quarkus)
Purpose: Global exception handling for alert processing
```

- Catches unhandled exceptions
- Logs error details
- Returns HTTP 500 with structured ErrorResponse
- Prevents stack traces from leaking to clients

---

### 2. Core Domain (Business Logic)

#### Domain Models

**Alert**
```java
Location: com.causa.core.domain.Alert
Pattern: Immutable domain model with Builder pattern
```

**Fields**:
```java
private final String alertId;           // Unique ID: {container}-{epochMillis}
private final Instant timestamp;        // When alert fired
private final String alertName;         // Prometheus alert rule name
private final AlertSeverity severity;   // CRITICAL, WARNING, INFO
private final String podName;           // Kubernetes pod (nullable)
private final String containerName;     // Container name
private final String namespace;         // Kubernetes namespace
private final AlertStatus status;       // FIRING, RESOLVED
private final boolean hasDiagnostics;   // Diagnostic analysis available
```

**Business Methods**:
```java
public static String generateAlertId(String containerName, Instant timestamp)
  → Generates deterministic ID: "payment-app-1779296400"
  → Sanitizes null containers to "unknown"
  
public String getCooldownKey()
  → Returns: "{alertName}:{podName}" or "{alertName}:{namespace}"
  → Used for deduplication
```

**AlertSeverity Enum**
```java
Location: com.causa.common.constants.AlertConstants.AlertSeverity
Values: CRITICAL, WARNING, INFO
Methods:
  • getValue(): Returns lowercase string ("critical")
  • fromString(String): Parses case-insensitive
  • isAtLeast(AlertSeverity): Ordinal comparison (CRITICAL > WARNING > INFO)
```

**AlertStatus Enum**
```java
Location: com.causa.common.constants.AlertConstants.AlertStatus
Values: FIRING, RESOLVED
Methods:
  • getValue(): Returns lowercase string ("firing")
  • fromString(String): Parses case-insensitive
```

#### Services

**AlertService (Interface)**
```java
Location: com.causa.core.services.AlertService
Purpose: Primary port for alert business logic
```

**Methods**:
```java
List<Alert> processAlerts(List<Alert> alerts)
  → Applies filtering pipeline:
    1. Severity filter (configurable minimum)
    2. Namespace filter (ignore system namespaces)
    3. Cooldown filter (deduplicate repeat alerts)
  → Persists accepted alerts
  → Returns accepted alerts for diagnostic triggering

boolean isInCooldown(Alert alert)
  → Checks if alert is in cooldown period
  → Uses getCooldownKey() for lookup
  → Returns true if within cooldown window
```

**AlertServiceImpl**
```java
Location: com.causa.core.services.impl.AlertServiceImpl
Scope: @ApplicationScoped (CDI)
```

**Dependencies**:
- `AlertConfig`: Configuration properties
- `AlertRepository`: Persistence port
- `ConcurrentHashMap<String, Instant>`: In-memory cooldown cache

**Initialization**:
```java
@PostConstruct
void init()
  → Parses minimum severity from config
  → Builds ignored namespaces set
  → Logs configuration
```

**Filtering Pipeline**:
```java
processAlerts(List<Alert> alerts)
  FOR each alert:
    IF !passesSeverityFilter(alert) → skip (log debug)
    IF !passesNamespaceFilter(alert) → skip (log debug)
    IF isInCooldown(alert) → skip (log debug)
    
    ELSE:
      → Record cooldown timestamp
      → Persist to repository
      → Add to accepted list
      → Log acceptance
  
  RETURN accepted list
```

**Cooldown Cleanup**:
```java
@Scheduled(every = "5m")
void cleanupCooldownCache()
  → Removes expired cooldown entries
  → Prevents unbounded memory growth
  → Logs cleanup statistics
```

#### Repository Ports (Secondary Ports)

**AlertRepository (Interface)**
```java
Location: com.causa.core.ports.AlertRepository
Purpose: Abstraction over persistence layer
```

**Methods**:
```java
Alert save(Alert alert)
  → Persists alert to database
  → Returns saved alert (with DB-generated metadata if any)

Optional<Alert> findById(String alertId)
  → Retrieves alert by unique ID
  → Returns Optional.empty() if not found

List<Alert> findByContainerName(String containerName)
  → Queries alerts for specific container
  → Used by GET /containers/{containerName}/alerts endpoint

List<Alert> findAll()
  → Retrieves all stored alerts
  → Used by GET /alerts endpoint

void updateHasDiagnostics(String alertId, boolean hasDiagnostics)
  → Marks alert as having diagnostic analysis
  → Called after diagnostic pipeline completes
```

---

### 3. Infrastructure Layer (Secondary Adapters)

#### Repository Implementation

**AlertRepositoryImpl**
```java
Location: com.causa.infrastructure.persistence.repositories.AlertRepositoryImpl
Implements: AlertRepository
Scope: @ApplicationScoped
```

**Dependencies**:
- `EntityManager`: JPA for database access
- `AlertEntityMapper`: Domain ↔ Entity conversion

**Implementation Strategy**:
```java
save(Alert alert)
  1. Map domain Alert → AlertEntity
  2. Persist entity via EntityManager
  3. Map entity back → domain Alert
  4. Return saved Alert

findById(String alertId)
  1. Query: SELECT e FROM AlertEntity e WHERE e.alertId = :id
  2. If found → map entity to domain
  3. Return Optional

findByContainerName(String containerName)
  1. Query: SELECT e FROM AlertEntity e WHERE e.containerName = :name
  2. Map List<AlertEntity> → List<Alert>
  3. Return list

findAll()
  1. Query: SELECT e FROM AlertEntity e
  2. Map entities to domain models
  3. Return list
```

#### Entity Mappers

**AlertEntityMapper**
```java
Location: com.causa.infrastructure.persistence.mappers.AlertEntityMapper
Purpose: Bidirectional mapping between Alert domain and AlertEntity JPA entity
```

**Mapping Strategy**:
```java
toEntity(Alert domain)
  → Maps domain fields to entity fields
  → Converts enums to string values (getValue())
  → Preserves immutability (domain) vs mutability (entity)

toDomain(AlertEntity entity)
  → Maps entity fields to domain fields
  → Parses string values back to enums (fromString())
  → Uses Builder pattern for domain construction
```

#### Database Schema

**AlertEntity**
```java
@Entity
@Table(name = "alerts")
```

**Columns**:
```sql
alert_id          VARCHAR(512)  PRIMARY KEY
timestamp         TIMESTAMP     NOT NULL
alert_name        VARCHAR(255)  NOT NULL
severity          VARCHAR(20)   NOT NULL  -- 'critical', 'warning', 'info'
pod_name          VARCHAR(255)  NULL
container_name    VARCHAR(255)  NOT NULL
namespace         VARCHAR(255)  NOT NULL
status            VARCHAR(20)   NOT NULL  -- 'firing', 'resolved'
has_diagnostics   BOOLEAN       NOT NULL DEFAULT FALSE
created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
```

---

## Data Flow

### Webhook Ingestion Flow

```
┌─────────────────┐
│  Prometheus     │
│  Alertmanager   │
└────────┬────────┘
         │ HTTP POST
         │ /api/v1/webhooks/alerts
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. AlertWebhookController.receiveAlerts()                   │
│    • Logs incoming request                                  │
│    • Validates payload via AlertWebhookValidator            │
│    • Returns 400 Bad Request if validation fails            │
└────────┬────────────────────────────────────────────────────┘
         │ Valid payload
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. AlertMapper.toDomainList()                               │
│    • Extracts labels from webhook payload                   │
│    • Parses timestamps (ISO 8601)                           │
│    • Generates alert IDs (container-epochMillis)            │
│    • Creates immutable Alert domain objects                 │
└────────┬────────────────────────────────────────────────────┘
         │ List<Alert>
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. AlertService.processAlerts()                             │
│    ┌─────────────────────────────────────────────────────┐ │
│    │ FOR each alert:                                     │ │
│    │   • Check severity filter (configurable minimum)   │ │
│    │   • Check namespace filter (ignore system NS)      │ │
│    │   • Check cooldown (deduplicate by pod/namespace)  │ │
│    │   • IF passes all filters:                         │ │
│    │       - Record cooldown timestamp                  │ │
│    │       - Persist to database via repository         │ │
│    │       - Add to accepted list                       │ │
│    └─────────────────────────────────────────────────────┘ │
└────────┬────────────────────────────────────────────────────┘
         │ List<Alert> (accepted)
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. DiagnosticService.triggerDiagnostics()                   │
│    • Creates Diagnostic in PENDING status                   │
│    • Persists to diagnostics table                          │
│    • Updates alert.hasDiagnostics = true                    │
│    • [Future] Triggers async LLM pipeline                   │
└────────┬────────────────────────────────────────────────────┘
         │ List<Diagnostic>
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. AlertResponse.accepted()                                 │
│    • Builds response DTO                                    │
│    • Status: "accepted" | "partial" | "rejected"            │
│    • Includes: acceptedAlertIds, diagnosticIds, counts      │
└────────┬────────────────────────────────────────────────────┘
         │ HTTP 200 OK
         ▼
┌─────────────────┐
│  Alertmanager   │
│  (receives ack) │
└─────────────────┘
```

### Query Flow (GET /api/v1/alerts)

```
┌─────────────────┐
│  HTTP Client    │
│  (Grafana, CLI) │
└────────┬────────┘
         │ GET /api/v1/alerts
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. AlertQueryController.getAllAlerts()                      │
│    • No validation needed (no parameters)                   │
│    • Logs query request                                     │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. AlertRepository.findAll()                                │
│    • Executes: SELECT e FROM AlertEntity e                  │
│    • Maps entities → domain models                          │
└────────┬────────────────────────────────────────────────────┘
         │ List<Alert>
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. AlertResponseMapper.toResponseList()                     │
│    • Converts domain models to DTOs                         │
│    • Maps enums to string values                            │
└────────┬────────────────────────────────────────────────────┘
         │ List<AlertDetailsResponse>
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. AlertListResponse.of()                                   │
│    • Wraps list in response envelope                        │
│    • Adds totalCount metadata                               │
└────────┬────────────────────────────────────────────────────┘
         │ HTTP 200 OK + JSON
         ▼
┌─────────────────┐
│  HTTP Client    │
└─────────────────┘
```

---

## Configuration

### Environment Variables

All configuration is externalized via environment variables:

```yaml
# Alert Filtering
CAUSA_ALERT_SEVERITY: "critical"           # Minimum severity to process
CAUSA_ALERT_COOLDOWN: "15"                 # Cooldown period in minutes
CAUSA_ALERT_IGNORE_NS: "kube-system,istio-system"  # Ignored namespaces
```
