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
- [Profile-Based Configuration](#profile-based-configuration)


---

## Overview

Causa Backend uses PostgreSQL 17 with the pgvector extension for data persistence and vector similarity search. The database layer consists of:

- **Connection Pool:** Agroal (Quarkus built-in)
- **ORM:** Hibernate ORM with Panache
- **Schema Management:** Flyway (runs migrations on startup)
- **Architecture Layer:** `infrastructure/persistence/` (Secondary Adapter)
- **Dev Services:** Quarkus Dev Services (automatic containerised PostgreSQL for local dev — zero config)

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

| Property | Environment Variable | Profile | Default | Description |
|----------|---------------------|---------|---------|-------------|
| `quarkus.datasource.db-kind` | - | all | `postgresql` | Database type |
| `quarkus.datasource.jdbc.url` | `CAUSA_DB_URL` | `%prod` | `jdbc:postgresql://iri-db-rw:5432/iri-db` | JDBC connection URL |
| `quarkus.datasource.username` | `CAUSA_DB_USERNAME` | `%prod` | `causa_backend` | Database username |
| `quarkus.datasource.password` | `CAUSA_DB_PASSWORD` | `%prod` | _(empty — required via Secret)_ | Database password |
| `quarkus.datasource.username` | - | `%dev` | `causa_dev` | Dev Services container username |
| `quarkus.datasource.password` | - | `%dev` | `dev_password` | Dev Services container password |

> **Note:** In `%dev`, `jdbc.url` is intentionally absent so Quarkus Dev Services activates automatically. In `%prod`, the URL defaults to the CloudNativePG service inside the cluster; override with `CAUSA_DB_URL` for external deployments.

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
| `quarkus.hibernate-orm.database.generation` | `none` | Schema managed exclusively by Flyway — Hibernate must not touch DDL |
| `quarkus.hibernate-orm.log.sql` | `false` | SQL statement logging (enable temporarily for debugging) |

---

## Local Development Setup

### Option 1: Quarkus Dev Services (Recommended — Zero Config)

**No database setup required.** When you run `./mvnw quarkus:dev`, Quarkus Dev Services automatically:

1. Pulls `pgvector/pgvector:pg17` from Docker Hub (cached after first pull)
2. Starts a temporary PostgreSQL 17 container with the `vector` extension pre-installed
3. Runs [`src/main/resources/db/dev-init.sql`](../../src/main/resources/db/dev-init.sql) (`CREATE EXTENSION IF NOT EXISTS vector`)
4. Runs Flyway migrations against it
5. Tears the container down on `Ctrl+C`

```bash
# That's it — just run:
./mvnw quarkus:dev
```

Dev Services container credentials (wired automatically — no `.env` needed):

| Setting | Value |
|---|---|
| Database | `iri-db` |
| Username | `causa_dev` |
| Password | `dev_password` |
| Port | Randomly assigned by Testcontainers; Quarkus wires it automatically |

**Data persistence per session:**

| Action | Container | Data |
|---|---|---|
| Hot reload (file save) | ✅ Keeps running | ✅ Persisted |
| `s` restart (dev console) | ✅ Keeps running | ✅ Persisted |
| `Ctrl+C` → `./mvnw quarkus:dev` | New container started | ❌ Wiped (Flyway re-runs migrations) |

> **Why `pgvector/pgvector:pg17` and not the production image?**
> The production image (`quay.io/rh-ee-shesaxen/postgres-pgvector:17`) is built on the CloudNativePG base which bakes CNPG-specific CMD flags (`--max_prepared_transactions=100`) into the container. Testcontainers launches the container directly without the CNPG operator, causing an OCI runtime crash. `pgvector/pgvector:pg17` is the correct standalone image — same Postgres 17 version, same `vector` extension, no CNPG dependency.

### Option 2: Docker PostgreSQL (Manual)

Use this if you want full control over the database container or need data to persist across `Ctrl+C` restarts.

Start PostgreSQL 17 with pgvector using Docker:

```bash
docker run -d \
  --name causa-postgres \
  -e POSTGRES_USER=causa_backend \
  -e POSTGRES_PASSWORD=dev_password \
  -e POSTGRES_DB=diagnostics-tool-db \
  -p 5432:5432 \
  pgvector/pgvector:pg17
```

Then initialise the vector extension:

```bash
docker exec causa-postgres psql -U causa_backend -d diagnostics-tool-db \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

Set environment variables:
The `application.yml` defaults to the Kubernetes service URL. For local development, override with environment variables:

```bash
export CAUSA_DB_URL="jdbc:postgresql://localhost:5432/diagnostics-tool-db"
export CAUSA_DB_USERNAME="causa_backend"
export CAUSA_DB_PASSWORD="dev_password"
```

**Note:** For LLM features, also export `VERTEX_PROJECT_ID=<your-gcp-project-id>` before starting.

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

## Profile-Based Configuration

The database URL is split across Quarkus profiles so Dev Services activates cleanly in development without polluting production config:

| Profile | Activated by | URL behaviour |
|---|---|---|
| `%dev` | `./mvnw quarkus:dev` | No `jdbc.url` set → Dev Services starts `pgvector/pgvector:pg17` automatically |
| `%prod` | `java -jar` / `docker run` | `${CAUSA_DB_URL:jdbc:postgresql://iri-db-rw:5432/iri-db}` (override via env var) |

The base config block contains only shared pool tuning (min/max size, timeouts, validation). Credentials and URL live exclusively in their respective profile.

---

## References

- [Quarkus Datasource Guide](https://quarkus.io/guides/datasource)
- [Quarkus Dev Services](https://quarkus.io/guides/dev-services)
- [Quarkus Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Agroal Connection Pool](https://agroal.github.io/)
- [pgvector/pgvector Docker Hub](https://hub.docker.com/r/pgvector/pgvector)
