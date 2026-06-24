# Causa Observability Integration User Flow

## Overview

This document describes the proposed user experience for integrating Causa with observability platforms such as Datadog, Grafana, Instana, Dynatrace, and Prometheus.

**Important:** Causa connects to users' existing observability tools via API. We do NOT install or manage observability agents. Users must have their observability agent (Datadog Agent, Grafana Agent, etc.) already deployed and running in their cluster or infrastructure. Causa only configures monitors, alerts, and dashboards via the observability platform's API.

---

## Proposed User Flow

### Step 1: Post Causa Installation

**Welcome to Causa**

Select your Observability Platform:

```
( ) Datadog
( ) Grafana
( ) Instana
( ) Dynatrace
```

---

### Step 2: Configure Provider

**For Datadog:**

```
Datadog Configuration

Datadog Site:
[ us5.datadoghq.com ]

API Key:
[ *************** ]

Application Key:
[ *************** ]

[ Validate Connection ]
```

**For Grafana:**

```
Grafana Configuration

Grafana URL:
[ https://grafana.example.com ]

API Token:
[ *************** ]

[ Validate Connection ]
```


---

### Step 3: Prerequisites Check

Causa verifies:

```
Checking Prerequisites

✓ API connection successful
✓ Required permissions granted
✓ Observability agent detected in cluster (optional check)

Detected Causa Metrics Endpoint:
✓ /q/metrics

Detected Metrics:
✓ causa_rca_analysis_available
✓ causa_rca_analysis_failed_total
✓ causa_rca_analysis_duration_seconds
✓ causa_rca_analysis_completed_total

Note: Ensure your Datadog Agent is configured to scrape /q/metrics
```

---

### Step 4: Configuration Summary

**Actions to be performed:**

```
✓ Create RCA Monitors via Datadog API
✓ Configure Alert Notification Channels
✓ Set up Dashboard Widgets (optional)
✓ Validate API connectivity

Note: Your existing Datadog Agent will collect metrics from /q/metrics
```

---

### Step 5: Configure Monitors

```
[ Configure Monitors ]
```

Backend performs:

```
POST /api/v1/integrations/configure
```

This endpoint configures monitors in your Datadog account:
1. Validates API credentials
2. Creates monitors in Datadog via API
3. Configures alert thresholds
4. Returns list of created monitors

**User Responsibility:** Configure your existing Datadog Agent to scrape Causa's /q/metrics endpoint.

---

### Step 6: Success

```
Datadog Integration Configured

API Connection: Active
Monitors Created: 6
Alert Channels: Configured

Next Steps:
- Ensure your Datadog Agent scrapes /q/metrics endpoint
- Configure agent with annotation discovery or static config

[ View Monitors in Datadog ]
[ Send Test RCA Event ]
[ Agent Configuration Guide ]
```

---

## Backend APIs

### 1. Validate Credentials

**Endpoint:**
```
POST /api/v1/integrations/{provider}/validate
```

**Request (Datadog):**
```json
{
  "site": "us5.datadoghq.com",
  "apiKey": "xxx",
  "appKey": "yyy"
}
```

**How Validation Works:**

For **Datadog**:
1. Makes a test API call to Datadog's validation endpoint:
   ```
   GET https://api.{site}/api/v1/validate
   Headers: DD-API-KEY: {apiKey}, DD-APPLICATION-KEY: {appKey}
   ```
2. Checks if the API returns 200 OK
3. Verifies required permissions (monitors:write, metrics:read)
4. Returns validation result

For **Grafana**:
1. Makes a test API call to Grafana's health endpoint:
   ```
   GET {grafanaUrl}/api/health
   Headers: Authorization: Bearer {apiToken}
   ```
2. Verifies API token has required permissions
3. Returns validation result


**Response:**
```json
{
  "valid": true,
  "message": "Credentials validated successfully",
  "permissions": [
    "metrics_read",
    "monitors_write",
    "dashboards_write"
  ]
}
```

**Error Response:**
```json
{
  "valid": false,
  "message": "Invalid API key or insufficient permissions",
  "error": "403 Forbidden",
  "missingPermissions": [
    "monitors_write"
  ]
}
```

---

### 2. Configure Monitors

**Endpoint:**
```
POST /api/v1/integrations/configure
```

**Request:**
```json
{
  "provider": "datadog",
  "config": {
    "site": "us5.datadoghq.com",
    "apiKey": "xxx",
    "appKey": "yyy"
  }
}
```

**What this endpoint does:**
1. Validates Datadog API credentials
2. Creates 6 monitors in your Datadog account via API
3. Configures alert thresholds for RCA metrics
4. Returns list of created monitors

**What this endpoint does NOT do:**
- Does NOT install Datadog Agent
- Does NOT configure agent scraping
- Does NOT deploy any resources to your cluster

**User must separately:**
- Ensure their Datadog Agent is running (in same or different cluster)
- Configure agent to scrape Causa's /q/metrics endpoint

**Response:**
```json
{
  "integrationId": "int-12345",
  "provider": "datadog",
  "status": "configured",
  "monitorsCreated": 6,
  "metricsEndpoint": "http://causa-service.causa-namespace.svc.cluster.local:8080/q/metrics",
  "createdAt": "2026-06-10T10:00:00Z",
  "notes": "Monitors created successfully. Ensure your Datadog Agent scrapes the metrics endpoint.",
  "monitors": [
    {
      "id": "mon-1",
      "name": "Causa RCA Generation Failure",
      "status": "active"
    },
    {
      "id": "mon-2",
      "name": "Causa RCA Generation Latency High",
      "status": "active"
    },
    {
      "id": "mon-3",
      "name": "Causa RCA - High Failure Rate",
      "status": "active"
    },
    {
      "id": "mon-4",
      "name": "Causa RCA - No Analysis Generated",
      "status": "active"
    },
    {
      "id": "mon-5",
      "name": "Causa RCA - Slow Analysis Trend",
      "status": "active"
    },
    {
      "id": "mon-6",
      "name": "Causa RCA Success Rate Low",
      "status": "active"
    }
  ]
}
```

---

### 3. Get Integration Status

**Endpoint:**
```
GET /api/v1/integrations/{integrationId}/status
```

**Response:**
```json
{
  "integrationId": "int-12345",
  "provider": "datadog",
  "configured": true,
  "apiConnectionHealthy": true,
  "monitorsCreated": 6,
  "metricsEndpoint": "http://causa-service.causa-namespace.svc.cluster.local:8080/q/metrics",
  "monitors": [
    {
      "name": "Causa RCA Generation Failure",
      "status": "OK",
      "lastTriggered": null
    },
    {
      "name": "Causa RCA Generation Latency High",
      "status": "OK",
      "lastTriggered": null
    },
    {
      "name": "Causa RCA - High Failure Rate",
      "status": "OK",
      "lastTriggered": null
    },
    {
      "name": "Causa RCA - No Analysis Generated",
      "status": "OK",
      "lastTriggered": null
    },
    {
      "name": "Causa RCA - Slow Analysis Trend",
      "status": "OK",
      "lastTriggered": null
    },
    {
      "name": "Causa RCA Success Rate Low",
      "status": "OK",
      "lastTriggered": null
    }
  ]
}
```

---

### 4. List Integrations

**Endpoint:**
```
GET /api/v1/integrations
```

**Response:**
```json
{
  "integrations": [
    {
      "integrationId": "int-12345",
      "provider": "datadog",
      "status": "active",
      "createdAt": "2026-06-10T10:00:00Z",
      "lastHealthCheck": "2026-06-10T10:30:00Z"
    }
  ]
}
```

---

### 5. Delete Integration

**Endpoint:**
```
DELETE /api/v1/integrations/{integrationId}
```

**Response:**
```json
{
  "integrationId": "int-12345",
  "status": "deleted",
  "message": "Integration and all associated resources removed successfully"
}
```

---

## Architecture

```text
Frontend (React/Angular)
   |
   | HTTP/REST
   v
Causa Integration APIs
   |
   +---- Datadog APIs (External)
   |         |
   |         +---- Create Monitors
   |         +---- Validate API Keys
   |         +---- Configure Alert Channels
   |         +---- Update Dashboards
   |
   +---- Grafana APIs (External)
   |         |
   |         +---- Create Dashboards
   |         +---- Configure Data Sources
   |         +---- Setup Alerts
   |
   +---- Other Provider APIs
             |
             +---- Platform-specific configuration


┌─────────────────────────────────────────────────────────────┐
│  User's Infrastructure (Same or Different Cluster)         │
│                                                             │
│  ┌──────────────────┐         ┌─────────────────┐          │
│  │ Datadog Agent    │ scrapes │ Causa Service   │          │
│  │ (User Managed)   │────────>│ /q/metrics      │          │
│  └──────────────────┘         └─────────────────┘          │
│         │                                                   │
│         │ sends metrics                                    │
│         v                                                   │
│  ┌──────────────────┐                                      │
│  │ Datadog Platform │                                      │
│  │ (SaaS)           │<──── Causa configures monitors       │
│  └──────────────────┘       via API                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Future-proof Design

The same UI works for multiple observability platforms:

- **Datadog**
- **Grafana**
- **Instana**
- **Dynatrace**

Only the backend provider implementation changes.

### Provider Interface

```java
public interface IntegrationProvider {
    ValidationResult validateCredentials(ProviderConfig config);
    InstallationResult install(InstallationRequest request);
    StatusResult getStatus();
    void cleanup();
}
```

### Provider Implementations

```text
IntegrationProvider (interface)
   |
   +---- DatadogProvider
   |         |
   |         +---- DatadogApiClient (API connection)
   |         +---- DatadogMonitorManager (Create/Update monitors)
   |         +---- DatadogAlertConfigurator (Configure notifications)
   |
   +---- GrafanaProvider
   |         |
   |         +---- GrafanaApiClient
   |         +---- GrafanaDashboardManager
   |         +---- GrafanaAlertManager
   |
   +---- InstanaProvider
   |         |
   |         +---- InstanaApiClient
   |         +---- InstanaAlertManager
   |
   +---- DynatraceProvider
             |
             +---- DynatraceApiClient
             +---- DynatraceMonitorManager
```

All providers consume the same RCA metrics from:

```
/q/metrics
```

---

## Benefits

### 1. Unified Experience
- Single UI for all observability platforms
- Consistent workflow regardless of provider
- Reduced learning curve

### 2. Automated Configuration
- No manual script execution
- Single API call for complete monitor setup
- Automated monitor and alert creation via API
- Validation at each step

### 3. Self-Service
- Users can configure integrations themselves
- No need for platform team intervention
- Immediate feedback on configuration

### 4. Extensibility
- Easy to add new providers
- Provider-specific logic isolated
- Common metrics endpoint

### 5. Maintainability
- Centralized integration logic
- API-driven configuration
- Version-controlled monitor definitions

---

## Implementation Phases

### Phase 1: Core APIs ✅
- Credential validation endpoint
- Monitor configuration endpoint
- Status endpoints

### Phase 2: Datadog Provider ✅
- Monitor creation via API
- Credential validation
- Monitor status tracking

### Phase 3: UI Development
- Configuration wizard
- Provider selection
- Monitor status dashboard
- Agent configuration guide

### Phase 4: Additional Providers
- Grafana
- Instana
- Dynatrace
- Prometheus

### Phase 5: Advanced Features
- Custom monitor templates
- Alert routing configuration
- Multi-account support

---

## Security Considerations

### Credential Storage
- API keys encrypted at rest
- Secrets stored in Kubernetes Secrets
- No credentials in logs

### Access Control
- RBAC for integration management
- Audit logging for all operations
- Namespace isolation

### Network Security
- TLS for all external communications
- Service mesh integration
- Network policies

---

## Monitoring the Integration

### Health Checks
- Agent connectivity
- Metrics scraping status
- Monitor status
- API connectivity

### Metrics
- Integration installation success rate
- Agent health status
- Monitor trigger frequency
- API response times

### Alerts
- Integration installation failures
- Agent health degradation
- Monitor creation failures
- API connectivity issues

---

## Related Documentation

- [Observability RCA Architecture](./observability-rca-architecture.md)
- [Testing Guide](./TESTING-GUIDE.md)
- [Monitor Configurations](../../src/main/java/com/causa/observability/datadog/monitors/)

---