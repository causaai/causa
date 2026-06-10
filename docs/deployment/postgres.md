# PostgreSQL with pgvector Deployment

This guide covers deploying PostgreSQL 17 with pgvector extension using CloudNativePG operator for both Kind and OpenShift clusters.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Platform-Specific Details](#platform-specific-details)
- [Connection Information](#connection-information)


## Overview

### Components

- **Operator**: CloudNativePG v1.29.1 (Cloud Native PostgreSQL)
- **PostgreSQL Version**: 17
- **Extension**: pgvector (for vector similarity search)
- **Database**: diagnostics-tool-db
- **User**: causa_backend (auto-generated password)
- **Storage**: 10Gi (uses cluster default storage class)
- **Namespace**: diagnostics-tool

### Key Features

- ✅ Multi-platform support (Kind and OpenShift)
- ✅ Automated operator installation (OLM for OpenShift, direct manifest for Kind)
- ✅ OpenShift Security Context Constraints (SCC) compliant
- ✅ pgvector extension pre-installed
- ✅ Automatic credential management via Kubernetes secrets

## Architecture

### Deployment Flow

```mermaid
flowchart TD
    A[Cluster Type Detection]

    A --> B[Kind]
    A --> C[OpenShift]

    B --> D[Direct Manifest Installation]
    C --> E["OLM Subscription (certified)"]

    D --> F[cnpg-system namespace]
    E --> G[openshift-operators namespace]

    F --> H[CloudNativePG Operator]
    G --> H

    H --> I["PostgreSQL Cluster (diagnostics-tool-db)"]
```

## Quick Start

### 1. Build Custom PostgreSQL Image

The custom image includes PostgreSQL 17 with pgvector extension and OpenShift SCC compliance.

```bash
# Build and push the image with pgvector
cd scripts/postgres
./build-postgres-image.sh -p

# Or with custom tag
./build-postgres-image.sh -t 17.1 -p
```

**Image Features:**
- Based on `postgres:17` official image
- Includes `postgresql-17-pgvector` package
- OpenShift SCC compliant (group 0 permissions)
- Multi-platform support (amd64/arm64)

### 2. Deploy PostgreSQL

```bash
# Deploy on Kind cluster
./scripts/postgres/deploy-postgres.sh -c kind

# Deploy on OpenShift cluster
./scripts/postgres/deploy-postgres.sh -c openshift

# Deploy with custom image
./scripts/postgres/deploy-postgres.sh -c kind -i quay.io/myorg/postgres-pgvector:17
```

### 3. Terminate/Cleanup

```bash
# Remove PostgreSQL cluster and operator
./scripts/postgres/deploy-postgres.sh -c kind -t

# For OpenShift
./scripts/postgres/deploy-postgres.sh -c openshift -t
```

## Platform-Specific Details

### Kind (Kubernetes in Docker)

#### Installation Method: Direct Manifest

The operator is installed directly from the official CloudNativePG GitHub releases:

```bash
# Operator URL
https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.29/releases/cnpg-1.29.1.yaml
```

**Namespace:** `cnpg-system`

**Components Created:**
- Namespace: `cnpg-system`
- Deployment: `cnpg-controller-manager`
- ServiceAccount, RBAC, CRDs
- Webhook configurations

**Advantages:**
- ✅ Simple and fast installation
- ✅ No additional dependencies
- ✅ Direct version control
- ✅ Easy to upgrade/downgrade

### OpenShift

#### Installation Method: OLM (Operator Lifecycle Manager)

OpenShift uses OLM for operator management, which provides:
- Automated dependency resolution
- Upgrade management
- Operator health monitoring
- Certified operator catalog

**OLM Subscription:**

```yaml
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: cloudnative-pg
  namespace: openshift-operators
spec:
  channel: stable-v1              # Operator channel
  name: cloudnative-pg
  source: certified-operators     # Red Hat certified catalog
  sourceNamespace: openshift-marketplace
```

**Namespace:** `openshift-operators` (cluster-wide)

**Installation Flow:**

1. **Subscription Created** → OLM detects the subscription
2. **CSV (ClusterServiceVersion) Created** → Operator metadata and requirements
3. **Operator Deployed** → Controller manager pods started
4. **InstallPlan Executed** → CRDs and RBAC created

**Why OLM for OpenShift?**

1. **Security Context Constraints (SCC) Compliance**
   - OLM-installed operators automatically get proper SCC permissions
   - Handles UID/GID restrictions correctly
   - Manages security policies

2. **Certified Operators**
   - Red Hat certified and tested
   - Enterprise support available
   - Regular security updates

3. **Automated Management**
   - Automatic updates within channel
   - Dependency resolution
   - Health monitoring

4. **Multi-tenancy Support**
   - Proper namespace isolation
   - RBAC integration
   - Audit logging

#### Security Context Constraints (SCC)

OpenShift enforces strict security policies through SCCs. Our PostgreSQL deployment is SCC-compliant:

**Key SCC Requirements:**

1. **No Fixed UID**
   - OpenShift assigns random UIDs from namespace range
   - Our Dockerfile removes `USER 26` directive
   - Allows OpenShift to inject UID dynamically

2. **Group 0 Permissions**
   ```dockerfile
   # In Dockerfile
   RUN chgrp -R 0 /var/lib/postgresql /var/run/postgresql && \
       chmod -R g=u /var/lib/postgresql /var/run/postgresql
   ```
   - Files owned by group 0 (root group)
   - Group has same permissions as user
   - Allows any UID in group 0 to access files

3. **No Privileged Operations**
   - No root access required
   - No host path mounts
   - No privileged ports



**Why This Matters:**

- ❌ **Without SCC compliance**: Pods fail to start with permission errors
- ✅ **With SCC compliance**: Pods start successfully with any assigned UID
- 🔒 **Security**: Prevents privilege escalation and container breakout

## Connection Information

### From within Kubernetes/OpenShift

```yaml
Host: diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local
Port: 5432
Database: diagnostics-tool-db
User: causa_backend
Password: (stored in secret)
```

### Service Endpoints

CloudNativePG creates multiple service endpoints:

```bash
# Read-Write service (primary)
diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local:5432

# Read-Only service (replicas, if configured)
diagnostics-tool-db-ro.diagnostics-tool.svc.cluster.local:5432

# Any instance (for admin tasks)
diagnostics-tool-db.diagnostics-tool.svc.cluster.local:5432
```

### Get Credentials

```bash
# Get password
kubectl get secret diagnostics-tool-db-app -n diagnostics-tool \
  -o jsonpath='{.data.password}' | base64 -d

# Get username
kubectl get secret diagnostics-tool-db-app -n diagnostics-tool \
  -o jsonpath='{.data.username}' | base64 -d

# Get full connection URI
kubectl get secret diagnostics-tool-db-app -n diagnostics-tool \
  -o jsonpath='{.data.uri}' | base64 -d
```

### Connection String

```bash
# Standard format
postgresql://causa_backend:PASSWORD@diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local:5432/diagnostics-tool-db

# JDBC format
jdbc:postgresql://diagnostics-tool-db-rw.diagnostics-tool.svc.cluster.local:5432/diagnostics-tool-db
```

## Directory Structure

```
deployment/
└── postgres/
    ├── Dockerfile                    # Custom PostgreSQL image with pgvector
    └── postgres-cluster.yaml         # CloudNativePG cluster manifest


scripts/
└── postgres/
    ├── build-postgres-image.sh       # Multi-platform image build script
    └── deploy-postgres.sh            # Deployment script with OLM support

docs/
└── deployment/
    └── postgres.md                   # This documentation
```

## Resources

### Official Documentation
- [CloudNativePG Documentation](https://cloudnative-pg.io/)
- [CloudNativePG GitHub](https://github.com/cloudnative-pg/cloudnative-pg)
- [pgvector Documentation](https://github.com/pgvector/pgvector)

### OpenShift Specific
- [OpenShift OLM Documentation](https://docs.openshift.com/container-platform/latest/operators/understanding/olm/olm-understanding-olm.html)
- [OpenShift SCC Documentation](https://docs.openshift.com/container-platform/latest/authentication/managing-security-context-constraints.html)
- [Certified Operators Catalog](https://catalog.redhat.com/software/operators/search)


