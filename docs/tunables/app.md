# App Tunables

HTTP server, database, connection pool, logging, encryption key, and the `%dev` profile.
Most of these are deployment-time settings (env var / ConfigMap); the dev profile also
supports hot-reload through Quarkus Dev Services.

---

## HTTP server

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Port | `CAUSA_PORT` | `8080` | HTTP listening port |
| Bind address | `CAUSA_HOST` | `0.0.0.0` | Network interface to bind to |
| CORS origins | `CAUSA_CORS_ORIGINS` | `*` | Allowed origins (comma-separated). Restrict to your UI route in production |
| CORS methods | `CAUSA_CORS_METHODS` | `GET,POST,PUT,DELETE,OPTIONS` | Allowed HTTP methods |
| CORS headers | `CAUSA_CORS_HEADERS` | `*` | Allowed request headers |
| Swagger UI path | `CAUSA_SWAGGER_UI_PATH` | `/swagger-ui` | Path where Swagger UI is served |
| App name | `CAUSA_APP_NAME` | `causa-backend` | Shown in health and info endpoints |

Swagger UI is **disabled by default** in production (`always-include: false` in
[`application.yml`](../../src/main/resources/application.yml)). Enable it locally:

```bash
./mvnw quarkus:dev -Dquarkus.swagger-ui.always-include=true
# then open http://localhost:8080/swagger-ui
```

---

## Database

| Tunable | Env var | Default | Description |
|---|---|---|---|
| JDBC URL | `CAUSA_DB_URL` | `jdbc:postgresql://iri-db-rw:5432/iri-db` | Full JDBC URL. In cluster: the CloudNativePG service FQDN. On VM: external route or NodePort |
| Username | `CAUSA_DB_USERNAME` | `causa_backend` | Set via `causa-db-secrets` Secret — never in ConfigMap |
| Password | `CAUSA_DB_PASSWORD` | _(empty)_ | Set via `causa-db-secrets` Secret — never in ConfigMap |

### Creating the database secret

```bash
kubectl create secret generic causa-db-secrets \
  --from-literal=CAUSA_DB_USERNAME=causa_backend \
  --from-literal=CAUSA_DB_PASSWORD=CHANGE_ME \
  -n openshift-tuning
```

The secret is mounted as a mandatory `secretRef` (`optional: false`) — the pod will not
start without it.

### Rotating the password

```bash
kubectl patch secret causa-db-secrets \
  --type='json' \
  -p='[{"op":"replace","path":"/stringData/CAUSA_DB_PASSWORD","value":"NEW_PASSWORD"}]' \
  -n openshift-tuning

kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

---

## Connection pool (Agroal)

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Min pool size | `CAUSA_DB_POOL_MIN_SIZE` | `2` | Connections kept alive at all times |
| Max pool size | `CAUSA_DB_POOL_MAX_SIZE` | `10` | Maximum simultaneous connections |
| Idle removal | `CAUSA_DB_POOL_IDLE_REMOVAL` | `PT2M` | Idle connection eviction interval (ISO-8601 duration) |
| Max lifetime | `CAUSA_DB_POOL_MAX_LIFETIME` | `PT10M` | Maximum age of any connection before forced recycling |
| Acquisition timeout | `CAUSA_DB_POOL_ACQUISITION_TIMEOUT` | `PT5S` | Time to wait for a connection from the pool before throwing |
| Validation interval | `CAUSA_DB_POOL_VALIDATION_INTERVAL` | `PT2M` | Background `SELECT 1` validation interval |

These are deployment-time settings. Increase `CAUSA_DB_POOL_MAX_SIZE` for high-throughput
or load-testing deployments. Settings are present in [`application.yml`](../../src/main/resources/application.yml))

---

## Flyway migrations

| Setting | Value | Description |
|---|---|---|
| `migrate-at-start` | `true` | Runs pending migrations on every pod startup |
| `baseline-on-migrate` | `true` | Creates baseline `V0` if no Flyway history exists yet |
| `validate-on-migrate` | `true` | Fails startup if an applied migration's checksum changed |
| `out-of-order` | `false` | Rejects migrations applied out of version order |
| `locations` | `classpath:db/migration` | Migration script location |

Migration scripts live in
[`src/main/resources/db/migration/`](../../src/main/resources/db/migration/).
Naming convention: `V{version}__{description}.sql`

Settings are present in [`application.yml`](../../src/main/resources/application.yml))   [`application.yml`](../../src/main/resources/application.yml))

---

## Hibernate ORM

| Setting | Value | Notes |
|---|---|---|
| `database.generation` | `none` | Schema is owned exclusively by Flyway — Hibernate never touches DDL |
| `log.sql` | `false` | Set to `true` (or `QUARKUS_HIBERNATE_ORM_LOG_SQL=true`) to trace SQL locally |
| `physical-naming-strategy` | `CamelCaseToUnderscoresNamingStrategy` | Java `camelCase` fields map to `snake_case` DB columns |

Settings are present in [`application.yml`](../../src/main/resources/application.yml))

---

## Logging

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Root log level | `CAUSA_LOG_LEVEL` | `INFO` | Framework and all libraries. Values: `TRACE` · `DEBUG` · `INFO` · `WARN` · `ERROR` |
| App log level | `CAUSA_APP_LOG_LEVEL` | `INFO` | Scoped to `com.causa.*` only — use `DEBUG` for application-level traces |

The `kind` overlay automatically sets `CAUSA_LOG_LEVEL=DEBUG`.

Hibernate SQL logs are hard-set to `WARN` in [`application.yml`](../../src/main/resources/application.yml) to avoid flooding.
Override locally with:

```bash
QUARKUS_LOG_CATEGORY__ORG_HIBERNATE_SQL__LEVEL=DEBUG ./mvnw quarkus:dev
```

Settings are present in [`application.yml`](../../src/main/resources/application.yml))

---

## Cluster identity

| Tunable | Config API key | Env var | Default | Description |
|---|---|---|---|---|
| Cluster name | `CLUSTER_NAME` | `CAUSA_CLUSTER_NAME` | `default` | Human-readable label included in Diagnostics API responses |
| Target type | _(env-only)_ | `CAUSA_CLUSTER_TARGET_TYPE` | `cluster` | Platform selector — controls which MCP servers are called |

`CAUSA_CLUSTER_TARGET_TYPE` cannot be changed via the Config API. It must be set in the
ConfigMap before pod startup. Cluster name can be changed via config API. See [config-api-doc](../api/configs-api.md)

---

## Encryption key

The `CAUSA_ENCRYPTION_KEY` is a Base64-encoded 32-byte AES-256-GCM key that protects
sensitive Config API values stored in PostgreSQL.

Generate a production key:

```bash
openssl rand -base64 32
```

Create a Kubernetes Secret:

```bash
kubectl create secret generic causa-encryption \
  --from-literal=CAUSA_ENCRYPTION_KEY=<base64-output> \
  -n openshift-tuning
```

Add to `envFrom` in the deployment:

```yaml
- secretRef:
    name: causa-encryption
    optional: false
```

> The default key baked into `application.yml` is intentionally insecure.
> **Never deploy without overriding `CAUSA_ENCRYPTION_KEY`.**

---

## Dev profile defaults

Active automatically when Quarkus starts with `./mvnw quarkus:dev`.

| Setting | Dev default | Production default |
|---|---|---|
| `CAUSA_DB_URL` | `jdbc:postgresql://localhost:5432/iri-db` | `jdbc:postgresql://iri-db-rw:5432/iri-db` |
| `CAUSA_DB_PASSWORD` | `dev_password` | _(must be set via Secret)_ |
| `LLM_PROVIDER` | `vertex-ai-anthropic` | _(must be set via ConfigMap)_ |
| `LLM_MODEL_NAME` | `claude-sonnet-4-6` | _(must be set via ConfigMap)_ |
| `VERTEX_LOCATION` | `us-east5` | _(must be set via ConfigMap)_ |

Quick start with all dev defaults:

```bash
./mvnw quarkus:dev
```

Override specific values inline:

```bash
CAUSA_DB_URL=jdbc:postgresql://localhost:5432/iri-db \
CAUSA_DB_PASSWORD=mypass \
LLM_PROVIDER=anthropic \
LLM_API_KEY=sk-ant-api03-... \
./mvnw quarkus:dev
```
