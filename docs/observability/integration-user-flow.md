# Causa Observability Integration User Flow

## Overview

This document describes the proposed user experience for integrating Causa with observability platforms such as Datadog, Grafana, Instana, Dynatrace, and Prometheus.

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

### Step 3: Auto Discovery

Causa discovers:

```
Detected Metrics Endpoint

/q/metrics

Detected Metrics:
✓ causa_rca_analysis_available
✓ causa_rca_analysis_failed_total
✓ causa_rca_analysis_duration_seconds
✓ causa_rca_analysis_completed_total
```

---

### Step 4: Installation Summary

**Actions to be performed:**

```
✓ Install Datadog Agent
✓ Configure OpenMetrics Scraping
✓ Create RCA Monitors
✓ Create Alert Templates
✓ Validate Metrics
```

---

### Step 5: Install

```
[ Install Integration ]
```

Backend performs:

```
POST /api/v1/integrations/install
```

This single endpoint performs all installation steps:
1. Validates credentials
2. Installs agent (equivalent to `setup-datadog-agent.sh`)
3. Creates monitors (equivalent to `create-datadog-resources.sh`)
4. Validates metrics endpoint

---

### Step 6: Success

```
Datadog Integration Installed

Agent Status: Healthy
Metrics Discovered: 8
Monitors Created: 6

[ View Monitors ]
[ Send Test RCA Event ]
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

### 2. Install Integration

**Endpoint:**
```
POST /api/v1/integrations/install
```

**Request:**
```json
{
  "provider": "datadog",
  "site": "us5.datadoghq.com",
  "apiKey": "xxx",
  "appKey": "yyy"
}
```

**This single endpoint performs all installation steps:**
1. Validates credentials
2. Installs agent (equivalent to `setup-datadog-agent.sh`)
3. Creates monitors (equivalent to `create-datadog-resources.sh`)
4. Validates metrics endpoint

**Response:**
```json
{
  "integrationId": "int-12345",
  "provider": "datadog",
  "status": "installed",
  "agentInstalled": true,
  "agentStatus": "healthy",
  "monitorsCreated": 6,
  "metricsDiscovered": 8,
  "metricsEndpoint": "/q/metrics",
  "scrapeInterval": "30s",
  "createdAt": "2026-06-10T10:00:00Z",
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
  "installed": true,
  "agentHealthy": true,
  "metricsDiscovered": 8,
  "monitorsCreated": 6,
  "lastScrape": "2026-06-10T10:30:00Z",
  "scrapeStatus": "success",
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
   +---- OpenShift/Kubernetes APIs
   |         |
   |         +---- Install Datadog Agent
   |         +---- Create ConfigMaps
   |         +---- Deploy Resources
   |
   +---- Datadog APIs
   |         |
   |         +---- Create Monitors
   |         +---- Validate Keys
   |         +---- Configure Alerts
   |
   +---- Grafana APIs
             |
             +---- Create Dashboards
             +---- Configure Data Sources
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
   |         +---- DatadogAgentInstaller
   |         +---- DatadogMonitorCreator
   |         +---- DatadogMetricsValidator
   |
   +---- GrafanaProvider
   |         |
   |         +---- GrafanaDashboardCreator
   |         +---- GrafanaDataSourceConfig
   |
   +---- InstanaProvider
   |         |
   |         +---- InstanaAgentInstaller
   |         +---- InstanaAlertCreator
   |
   +---- DynatraceProvider
             |
             +---- DynatraceAgentInstaller
             +---- DynatraceMonitorCreator
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

### 2. Automated Setup
- No manual script execution
- Single API call for complete installation
- Automated agent installation and monitor creation
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

### Phase 1: Core APIs
- Integration validation endpoint
- Single installation endpoint
- Status endpoints

### Phase 2: Datadog Provider
- Agent installation logic
- Monitor creation logic
- Metrics validation

### Phase 3: UI Development
- Integration wizard
- Provider selection
- Status dashboard

### Phase 4: Additional Providers
- Grafana
- Instana
- Dynatrace

### Phase 5: Advanced Features
- Multi-provider support
- Custom monitor templates
- Alert routing configuration

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