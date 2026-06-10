# Database Setup and Configuration

This guide covers the PostgreSQL database connection pool setup for Causa Backend, including configuration, local development, and troubleshooting.

## Table of Contents

- [Overview](#overview)
- [Connection Pool (Agroal)](#connection-pool-agroal)
- [ORM Layer (Hibernate + Panache)](#orm-layer-hibernate--panache)
- [Configuration Reference](#configuration-reference)
- [Local Development Setup](#local-development-setup)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Embedding Strategy](#embedding-strategy)
- [Connection Pool Tuning](#connection-pool-tuning)


---

## Overview

Causa Backend uses PostgreSQL 17 with the pgvector extension for data persistence and vector similarity search. The database layer consists of:

- **Connection Pool:** Agroal (Quarkus built-in)
- **ORM:** Hibernate ORM with Panache 
- **Schema Management:** Hibernate auto-update (will migrate to Flyway later)
- **Architecture Layer:** `infrastructure/persistence/` (Secondary Adapter)

### Why Agroal?

Agroal is Quarkus's default connection pool implementation:
- ✅ High performance with minimal overhead
- ✅ Smart connection validation
- ✅ Integrated with Quarkus transaction management
- ✅ Production-ready defaults

### Why Hibernate ORM + Panache (Blocking)?

- REST layer uses blocking I/O (`quarkus-rest`)
- Simplifies repository pattern (aligns with hexagonal architecture)
- Panache provides Active Record pattern for common CRUD operations
- **Embedding operations bypass ORM** (see [Embedding Strategy](#embedding-strategy))

---

## Connection Pool (Agroal)

The connection pool is configured in `application.yml` and managed by the `DatabaseConnectionService` class.

### Connection Lifecycle

1. **Startup:** `DatabaseConnectionService` verifies connectivity on startup (priority 20)
2. **Runtime:** Pool maintains 2-10 connections (configurable)
3. **Health Check:** `/q/health/ready` includes database connectivity check
4. **Validation:** Background validation runs every 2 minutes

### DatabaseConnectionService

Located at: `src/main/java/com/causa/infrastructure/persistence/DatabaseConnectionService.java`

**Features:**
- `@ApplicationScoped` singleton bean
- Startup verification with priority 20
- Structured logging via `CausaLogger`

**Usage:**
```java
@Inject
DatabaseConnectionService dbService;

// Health check happens automatically via MicroProfile Health
// Check /q/health/ready endpoint
```

---

## ORM Layer (Hibernate + Panache)

### Base Entity

All entities extend `BaseEntity` which provides:
- `id` - BIGSERIAL primary key (inherited from `PanacheEntity`)
- `createdAt` - automatic creation timestamp
- `updatedAt` - automatic update timestamp

**File:** `src/main/java/com/causa/infrastructure/persistence/entity/BaseEntity.java`

### Example Entity: SystemHealthHistoryEntity

**File:** `src/main/java/com/causa/infrastructure/persistence/entity/SystemHealthHistoryEntity.java`

```java
@Entity
@Table(name = "system_health_history")
public class SystemHealthHistoryEntity extends BaseEntity {
    
    @NotNull
    @Column(name = "timestamp", nullable = false)
    public LocalDateTime timestamp;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false)
    public AppConstants.HealthStatus overallStatus;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_metrics", columnDefinition = "jsonb")
    public JsonNode componentMetrics;
}
```

### Panache Repository Pattern

```java
// Active Record pattern (methods on entity)
SystemHealthHistoryEntity history = new SystemHealthHistoryEntity();
history.timestamp = LocalDateTime.now();
history.overallStatus = AppConstants.HealthStatus.UP;
history.persist();

// Query examples
List<SystemHealthHistoryEntity> all = SystemHealthHistoryEntity.listAll();
SystemHealthHistoryEntity found = SystemHealthHistoryEntity.findById(1L);
long count = SystemHealthHistoryEntity.count();
```

---

## Configuration Reference

All database configuration is in `src/main/resources/application.yml`.

### Datasource Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `quarkus.datasource.db-kind` | - | `postgresql` | Database type |
| `quarkus.datasource.username` | `CAUSA_DB_USERNAME` | `causa_backend` | Database username |
| `quarkus.datasource.password` | `CAUSA_DB_PASSWORD` | _(empty)_ | Database password |
| `quarkus.datasource.jdbc.url` | `CAUSA_DB_URL` | K8s service URL | JDBC connection URL |

### Connection Pool Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `jdbc.min-size` | `CAUSA_DB_POOL_MIN_SIZE` | `2` | Minimum connections in pool |
| `jdbc.max-size` | `CAUSA_DB_POOL_MAX_SIZE` | `10` | Maximum connections in pool |
| `jdbc.idle-removal-interval` | `CAUSA_DB_POOL_IDLE_REMOVAL` | `PT2M` | How often to remove idle connections |
| `jdbc.max-lifetime` | `CAUSA_DB_POOL_MAX_LIFETIME` | `PT10M` | Maximum connection lifetime |
| `jdbc.acquisition-timeout` | `CAUSA_DB_POOL_ACQUISITION_TIMEOUT` | `PT5S` | Max wait time for connection |
| `jdbc.background-validation-interval` | `CAUSA_DB_POOL_VALIDATION_INTERVAL` | `PT2M` | Validation interval |
| `jdbc.new-connection-sql` | - | `SELECT 1` | SQL to validate new connections |

**Note:** All intervals use ISO 8601 duration format (`PT2M` = 2 minutes, `PT5S` = 5 seconds).

### Hibernate ORM Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.hibernate-orm.database.generation` | `update` | Auto-create/update schema from entities |
| `quarkus.hibernate-orm.log.sql` | `true` | Log SQL statements (set to `false` in production) |

---

## Local Development Setup

### Option 1: Docker PostgreSQL (Recommended)

Start PostgreSQL 17 with Docker:

```bash
docker run -d \
  --name causa-postgres \
  -e POSTGRES_USER=causa_backend \
  -e POSTGRES_PASSWORD=dev_password \
  -e POSTGRES_DB=diagnostics-tool-db \
  -p 5432:5432 \
  postgres:17
```

Set environment variables:

```bash
export CAUSA_DB_URL="jdbc:postgresql://localhost:5432/diagnostics-tool-db"
export CAUSA_DB_USERNAME="causa_backend"
export CAUSA_DB_PASSWORD="dev_password"
```

Start Quarkus dev mode:

```bash
./mvnw quarkus:dev
```

### Verify Connection

Check the startup logs for:

```
Verifying database connection on startup | database="agroal"
Database connection pool initialized successfully | dbKind="postgresql", pool="agroal"
```

Check the health endpoint:

```bash
curl http://localhost:8080/q/health/ready
```

---

## Kubernetes Deployment

### Connection Details

The application connects to PostgreSQL deployed via CloudNativePG operator:

- **Host:** `diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local`
- **Port:** `5432`
- **Database:** `diagnostics-tool-db`
- **User:** `causa_backend`
- **Password:** Injected from Kubernetes secret `diagnostics-tool-db-app`

### Configuration Sources

**ConfigMap:** `deployment/kubernetes/base/configmap.yaml`
```yaml
data:
  CAUSA_DB_URL: "jdbc:postgresql://diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local:5432/diagnostics-tool-db"
```

**Deployment:** `deployment/kubernetes/base/deployment.yaml`
```yaml
env:
  - name: CAUSA_DB_URL
    valueFrom:
      configMapKeyRef:
        name: causa-config
        key: CAUSA_DB_URL
  - name: CAUSA_DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: diagnostics-tool-db-app
        key: username
  - name: CAUSA_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: diagnostics-tool-db-app
        key: password
```


---

## Embedding Strategy

**Embeddings use native SQL and LangChain4J, NOT Hibernate ORM.**

### Why Separate?

- Vector operations (`INSERT`, `<->` cosine similarity) are performance-critical
- LangChain4J has built-in pgvector integration via JDBC
- Bypassing ORM avoids unnecessary object mapping overhead

### Both Approaches Share the Same Connection Pool

```
┌─────────────────────────────┐
│   Agroal Connection Pool    │
│     (min: 2, max: 10)        │
└──────────┬──────────┬────────┘
           │          │
    ┌──────▼──────┐   │
    │  Hibernate  │   │
    │  (CRUD)     │   │
    └─────────────┘   │
                      │
              ┌───────▼─────────┐
              │  Native SQL /   │
              │  LangChain4J    │
              │  (Embeddings)   │
              └─────────────────┘
```

**Result:** No wasted resources, no performance impact. Panache handles CRUD, native SQL handles vectors.

---

## Connection Pool Tuning

### Default Settings (Single Pod)

```yaml
min-size: 2        # Keep 2 warm connections
max-size: 10       # Up to 10 concurrent connections
idle-removal: PT2M # Remove idle connections after 2 minutes
max-lifetime: PT10M # Recycle connections after 10 minutes
```
---

## References

- [Quarkus Datasource Guide](https://quarkus.io/guides/datasource)
- [Quarkus Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Agroal Connection Pool](https://agroal.github.io/)
