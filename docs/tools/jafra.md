# Jafra

**J**VM **A**dvanced **F**light **R**ecording with **A**sync-profiler — a continuous profiling ecosystem for Java workloads running in Kubernetes. Jafra injects [async-profiler](https://github.com/async-profiler/async-profiler) into opted-in Pods without requiring any application changes, collects rotated JFR chunks from the node, and centralises analysis. CPU, allocation, lock, GC, and compilation events land in the same `.jfr` file because the controller defaults to `jfrsync=default` — this is set by the controller, not by the installer.

> **Kind only.** Jafra is not supported on OpenShift. Current release: `v0.0.1`.

## Components

| Component | Repo | Role |
|---|---|---|
| **jafra-controller** | [jafra-controller](https://github.com/bharathappali/jafra-controller) | Go mutating admission webhook. Validates Pod opt-in labels/annotations and injects async-profiler, a recording `hostPath` volume, and `JAVA_TOOL_OPTIONS` into targeted containers. Requires cert-manager for TLS. |
| **jafra-agent** | [jafra-agent](https://github.com/bharathappali/jafra-agent) | Rust DaemonSet. Watches `/var/lib/jafra/recordings` on each node, detects finalized JFR chunks from the 68-byte header, and streams them to the analyzer over gRPC. Deletes closed rotations after acknowledgement. |
| **jafra-analyzer** | [jafra-analyzer](https://github.com/bharathappali/jafra-analyzer) | Quarkus gRPC receiver. Accepts chunk streams, persists them to a PVC, stitches contiguous chunks into per-recording JFR files, and serves JMC-rule analysis and raw event summaries over HTTP. |

## How it works on Kind (v0.0.1)

```
Opted-in Java Pod
    │  labels: jafra.io/enabled=true, jafra.io/mode=continuous
    │  annotations: jafra.io/containers=<name>
    ▼
jafra-controller  (MutatingWebhook)
    │  injects async-profiler agent + hostPath recording volume
    │  appends -agentpath to JAVA_TOOL_OPTIONS
    ▼
/var/lib/jafra/recordings  (node hostPath)
    │  rotated .jfr chunks written by the JVM
    ▼
jafra-agent  (DaemonSet, one per node)
    │  detects finalized chunks via JFR header
    │  streams OpenChunk → ChunkFrame → CommitChunk over gRPC
    ▼
jafra-analyzer  (Deployment, port 9090 gRPC / 8080 HTTP)
    │  persists + stitches chunks on PVC
    │  serves /api/v1/recordings, /report, /summary
    ▼
(optional) Jafra MCP Server  (NodePort 30003)
    └─ exposes JFR summaries and JMC reports from the analyzer to Causa over MCP
```

To opt a Pod in, add to its metadata:

```yaml
labels:
  jafra.io/enabled: "true"
  jafra.io/mode: "continuous"
annotations:
  jafra.io/containers: "<container-name>"
```

## Analyzer storage

The analyzer PVC default is **10 Gi**. JFR disk usage grows with JVM event volume and application activity — busy workloads with `jfrsync=default` can fill this quickly. Jafra has no auto-pruning in the current release, so recordings accumulate until the volume is full; once full, ingest starts rejecting chunks.

If you see ingest failures or the volume nearing capacity, increase the PVC size before deploying:

```bash
# manifests/jafra/analyzer/deployment.yaml
storage: 10Gi   # increase as needed for your workload
```

> **Note:** auto-pruning and S3-compatible storage are planned for a future release.

## Umbrella repo

[jafra-io](https://github.com/bharathappali/jafra-io) — Kubernetes manifests, the shared `jafra.proto` contract, and git submodules for all three components above.
