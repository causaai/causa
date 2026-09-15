# API Documentation

This folder contains the current API documentation for the Causa backend. 

## Available docs

| Resource | Purpose | File |
|---|---|---|
| API index | Entry point for all documented endpoints in this folder | [`README.md`](docs/api/README.md) |
| Alerts and webhook API | Alert ingestion and alert retrieval endpoints | [`alerts-api.md`](docs/api/alerts-api.md) |
| Diagnostics API | Diagnostic listing and diagnostic detail endpoints | [`diagnostics-api.md`](docs/api/diagnostics-api.md) |
| Configuration API | Runtime config listing, lookup, and update endpoints | [`configs-api.md`](docs/api/configs-api.md) |
| Custom health endpoint | Aggregated application health endpoint | [`health-endpoint.md`](docs/api/health-endpoint.md) |
| Quarkus health checks | Liveness, readiness, and overall platform checks | [`health-checks.md`](docs/api/health-checks.md) |
| OpenAPI spec | OpenAPI YAML for the current API surface | [`openapi.yaml`](docs/api/openapi.yaml) |
| Postman collection | Importable request collection for manual testing | [`postman-collection.json`](docs/api/postman-collection.json) |

## Endpoint summary

| Method | Path | Description | Details |
|---|---|---|---|
| `POST` | `/api/v1/webhooks/alerts` | Ingest Alertmanager webhook payloads and trigger diagnostics | [`alerts-api.md`](docs/api/alerts-api.md) |
| `POST` | `/api/v1/alerts` | Manually create a synthetic alert to trigger diagnosis | [`alerts-api.md`](docs/api/alerts-api.md) |
| `GET` | `/api/v1/alerts` | List alerts, optionally filtered by `workload_name` and `namespace` | [`alerts-api.md`](docs/api/alerts-api.md) |
| `GET` | `/api/v1/alerts/{id}` | Fetch one alert by id | [`alerts-api.md`](docs/api/alerts-api.md) |
| `GET` | `/api/v1/diagnostics` | List diagnostic summaries | [`diagnostics-api.md`](docs/api/diagnostics-api.md) |
| `GET` | `/api/v1/diagnostics/{id}` | Fetch one diagnostic by id | [`diagnostics-api.md`](docs/api/diagnostics-api.md) |
| `GET` | `/api/v1/configs` | List runtime configs, optionally by category | [`configs-api.md`](docs/api/configs-api.md) |
| `GET` | `/api/v1/configs/{key}` | Fetch one runtime config by key | [`configs-api.md`](docs/api/configs-api.md) |
| `POST` | `/api/v1/configs` | Upsert runtime configs | [`configs-api.md`](docs/api/configs-api.md) |
| `GET` | `/api/v1/healthz` | Aggregated application health endpoint | [`health-endpoint.md`](docs/api/health-endpoint.md) |
| `GET` | `/q/health` | Quarkus overall health | [`health-checks.md`](docs/api/health-checks.md) |
| `GET` | `/q/health/live` | Quarkus liveness check | [`health-checks.md`](docs/api/health-checks.md) |
| `GET` | `/q/health/ready` | Quarkus readiness check | [`health-checks.md`](docs/api/health-checks.md) |

## Notes

- All examples in this folder are aligned to the running local app on `localhost:8080`. Please change the base url according to server's URL. 
- The webhook example payload used in [`alerts-api.md`](docs/api/alerts-api.md) is the Alertmanager payload you supplied for `causa-high-memory`.
- The generated IDs in examples are real values captured from the local app run and may differ in other environments.
- Keep [`openapi.yaml`](docs/api/openapi.yaml) and [`postman-collection.json`](docs/api/postman-collection.json) in sync whenever endpoints change.
