# Alert Tunables

Controls how Causa Backend ingests, filters, and rate-limits incoming Alertmanager webhooks
before they enter the diagnostic pipeline.

---

## All alert tunables

| Tunable | Config API key | Env var | Default | Description |
|---|---|---|---|---|
| Severity filter | `ALERT_FILTER_SEVERITY` | `CAUSA_ALERT_SEVERITY` | `critical` | Minimum severity to trigger analysis. Values: `critical` · `warning` · `info` |
| Cooldown period | `ALERT_COOLDOWN_MINUTES` | `CAUSA_ALERT_COOLDOWN` | `15` | Minutes before re-analysing the same pod. Prevents LLM spam on flapping alerts |
| Ignored namespaces | `ALERT_IGNORE_NAMESPACES` | `CAUSA_ALERT_IGNORE_NS` | `kube-system,istio-system` | Comma-separated namespaces whose alerts are silently dropped |
| Cooldown cleanup interval | `ALERT_COOLDOWN_CLEANUP_INTERVAL` | `CAUSA_ALERT_COOLDOWN_CLEANUP_INTERVAL` | `5m` | How often expired in-memory cooldown entries are purged (e.g. `5m`, `10m`, `1h`) |

All four keys are available at **both** the Config API layer (no restart) and the
environment variable layer (requires restart). Use the Config API for live tuning.

---

## Tuning via Config API (no restart)

```bash
# Lower the severity filter to also analyse warning-level alerts
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"ALERT_FILTER_SEVERITY": "warning"}}'

# Extend the cooldown to 30 minutes for a noisy environment
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"ALERT_COOLDOWN_MINUTES": "30"}}'

# Add a namespace to the ignore list
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"ALERT_IGNORE_NAMESPACES": "kube-system,istio-system,monitoring"}}'
```

Read current values at any time:

```bash
curl http://{{BASE_URL}}/api/v1/configs?category=alerts
```

---

## Tuning via ConfigMap (requires pod restart)

Edit [`deployment/kubernetes/base/configmap.yaml`](../../deployment/kubernetes/base/configmap.yaml):

```yaml
data:
  CAUSA_ALERT_SEVERITY:   "warning"
  CAUSA_ALERT_COOLDOWN:   "30"
  CAUSA_ALERT_IGNORE_NS:  "kube-system,istio-system,monitoring"
```

Apply and restart:

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

---

## Tuning on VM (requires service restart)

Edit `/opt/causa/.env`:

```env
CAUSA_ALERT_SEVERITY=warning
CAUSA_ALERT_COOLDOWN=30
CAUSA_ALERT_IGNORE_NS=kube-system,istio-system,monitoring
```

Restart:

```bash
sudo systemctl restart causa-backend
```


---

## How startup seeding works

On startup the application resolves these keys in order:

1. Checks the PostgreSQL `configurations` table (set via a previous Config API call).
2. Falls back to the env var / ConfigMap value.
3. Falls back to the hard-coded default.

The resolved value is written back to the DB so subsequent pods in the same fleet start
with the DB value without needing any env vars set.
