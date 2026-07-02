# BOB Shell Integration Guide

## Overview

This guide explains how to use IBM's BOB Shell CLI directly integrated into causa-backend. The `BobShellPromptSender` provides native BOB integration without requiring a separate wrapper service.

## Architecture

```
Causa Backend
    ↓
BobShellPromptSender (implements PromptSender)
    ↓
ProcessBuilder (executes BOB Shell CLI via stdin)
    ↓
BOB Shell (Node.js CLI tool)
    ↓
IBM BOB AI Service API
```

**Key Design Decision:** All prompts are sent via stdin (not command-line arguments) for maximum reliability and to avoid OS-specific ARG_MAX limitations.

## Prerequisites

### 1. BOB Shell Installation

BOB Shell must be installed in the environment where causa-backend runs:

**Local Development (macOS/Linux):**
```bash
curl -fsSL https://bob.ibm.com/download/bobshell.sh | bash
```

**Local Development (Windows):**
```powershell
powershell -ep Bypass 'irm -Uri "https://bob.ibm.com/download/bobshell.ps1" | iex'
```

**Docker/OpenShift:**
The Dockerfile automatically installs BOB Shell during image build using IBM's official installation script.

### 2. API Key Configuration

Set the `LLM_API_KEY` environment variable (same as other LLM providers):

```bash
export LLM_API_KEY=your-bob-api-key-here
```

**Note:** BOB Shell uses the same `LLM_API_KEY` environment variable as other providers (Claude, Vertex AI). The key value is provider-specific.

## Configuration

### application.yml

```yaml
causa:
  llm:
    provider: bob             # Activates BobShellPromptSender
    api-key: ${LLM_API_KEY:}  # Same key used by Claude — BOB Shell reads this via LLM_API_KEY
    timeout-seconds: 180      # Shared timeout — controls Process.waitFor() for BOB Shell
    bob:
      shell-path: bob         # BOB binary bundled in the image; default is "bob" (on PATH)
```

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `LLM_PROVIDER` | LLM provider to use | Yes | - |
| `LLM_API_KEY` | API key for authentication (provider-specific) | Yes | - |
| `LLM_TIMEOUT_SECONDS` | Timeout in seconds — controls process execution deadline for BOB Shell | No | `180` |

**Note:** BOB Shell uses the same `LLM_API_KEY` environment variable as other providers (Claude, Vertex AI). The key value is provider-specific.

### Kubernetes ConfigMap

For Kubernetes deployments, BOB Shell configuration can be set in the ConfigMap (`deployment/kubernetes/base/configmap.yaml`):

```yaml
# BOB Shell reuses shared LLM settings — increase timeout for long-running analysis
LLM_TIMEOUT_SECONDS: "180"
```

### Kubernetes Secret

The `LLM_API_KEY` should be stored in a Kubernetes Secret, not in the ConfigMap:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: causa-llm-secrets
type: Opaque
stringData:
  LLM_API_KEY: your-bob-api-key-here
```

## Usage

### Direct Usage (When Switching Logic is Implemented)

Once the provider switching logic is implemented by your team, you can use BOB Shell by:

1. Setting the provider in configuration:
```yaml
causa:
  llm:
    provider: bob
```

2. Or via environment variable:
```bash
export LLM_PROVIDER=bob
```

### Programmatic Usage

The `BobShellPromptSender` implements the `PromptSender` interface, so it can be used like any other LLM provider:

```java
@Inject
BobShellPromptSender bobShellPromptSender;

public void analyzeAlert(Alert alert) {
    LLMRequest request = LLMRequest.builder("Analyze this alert: " + alert.getMessage())
        .systemPrompt("You are an expert in root cause analysis.")
        .context("Alert severity: " + alert.getSeverity())
        .maxTokens(4096)
        .temperature(0.1)
        .build();
    
    LLMResponse response = bobShellPromptSender.send(request);
    
    System.out.println("Analysis: " + response.content());
    System.out.println("Tokens used: " + response.totalTokens());
}
```

## Features

### 1. Large Prompt Support

Automatically handles large prompts (>100KB) using stdin mode:

```java
// Small prompt: uses -p flag
String smallPrompt = "Analyze this code...";

// Large prompt (>100KB): automatically uses stdin mode
String largePrompt = readFile("large-rca-context.txt");  // 459KB

// Both work seamlessly
LLMRequest request = LLMRequest.builder(largePrompt).build();
LLMResponse response = bobShellPromptSender.send(request);
```

### 2. Token Usage Tracking

Extracts actual BOB token usage from response:

```java
LLMResponse response = bobShellPromptSender.send(request);

System.out.println("Prompt tokens: " + response.inputTokens());
System.out.println("Completion tokens: " + response.outputTokens());
System.out.println("Total tokens: " + response.totalTokens());
System.out.println("Latency: " + response.latencyMs() + "ms");
```

### 3. Automatic Timeout Management

Configurable timeout with automatic process termination:

```yaml
causa:
  llm:
    bob:
      timeout-seconds: 180  # 3 minutes
```

### 4. Health Check Integration

BOB Shell availability is checked during startup:

```java
// Automatically called during application startup
boolean available = bobShellPromptSender.checkAvailability();
```

## Response Format

BOB Shell returns structured JSON responses:

```json
{
  "issue_title": "High Memory Usage in Java Application",
  "root_cause": "Memory leak in connection pool...",
  "possible_solutions": [
    {
      "solution": "Implement connection pool monitoring",
      "confidence_score": 0.85
    }
  ],
  "confidence_score": 0.82,
  "evidence": [...],
  "recommendations": [...]
}
```

The `BobShellPromptSender` automatically extracts the clean JSON content from BOB's verbose output.

## Error Handling

### Common Errors

**1. BOB Shell Not Found**
```
Error: BOB Shell is not available
Solution: Install BOB Shell using official script:
         curl -fsSL https://bob.ibm.com/download/bobshell.sh | bash
```

**2. API Key Missing**
```
Error: LLM_API_KEY environment variable not set
Solution: Set the API key: export LLM_API_KEY=your-bob-api-key
```

**3. Timeout**
```
Error: BOB Shell execution timed out after 180 seconds
Solution: Increase timeout in configuration or optimize prompt size
```

**4. Process Execution Failed**
```
Error: BOB Shell failed with exit code 1
Solution: Check BOB Shell logs and verify API key is valid
```

## Performance Considerations

### Typical Performance Metrics

| Prompt Size | Processing Time | Token Usage | Cost |
|-------------|----------------|-------------|------|
| Small (1KB) | 5-10 seconds | ~1,000 tokens | ~$0.01 |
| Medium (50KB) | 30-60 seconds | ~50,000 tokens | ~$0.50 |
| Large (459KB) | 100-130 seconds | ~120,000 tokens | ~$1.20 |

### Optimization Tips

1. **Use Concise Prompts**: Remove unnecessary context
2. **Leverage System Prompts**: Reuse system instructions across requests
3. **Monitor Token Usage**: Track costs via `LLMResponse.totalTokens()`
4. **Adjust Timeouts**: Set appropriate timeouts based on expected prompt size

## Deployment

### Local Development

1. Install BOB Shell using official script:
```bash
# macOS/Linux
curl -fsSL https://bob.ibm.com/download/bobshell.sh | bash

# Windows
powershell -ep Bypass 'irm -Uri "https://bob.ibm.com/download/bobshell.ps1" | iex'
```

2. Verify installation:
```bash
which bob
bob --version
```

3. Set API key:
```bash
export LLM_API_KEY=your-bob-api-key
```

4. Run causa-backend:
```bash
./mvnw quarkus:dev
```

**Note:** The application will automatically detect BOB Shell in your PATH.

### Docker Build

```bash
# Build the application
./mvnw package

# Build Docker image (BOB Shell installed automatically via official script)
docker build -f src/main/docker/Dockerfile.jvm -t causa-backend:latest .

# Run with API key
docker run -e LLM_API_KEY=your-bob-api-key -p 8080:8080 causa-backend:latest
```

**What happens during build:**
1. Dockerfile installs curl
2. Downloads and runs IBM's official BOB Shell installation script: `https://bob.ibm.com/download/bobshell.sh`
3. BOB Shell is automatically configured and ready to use

**No registry configuration or authentication tokens needed!**

### OpenShift Deployment

#### Prerequisites

1. **Build and Push Image:**

```bash
# Build the application
./mvnw package

# Build and push image (BOB Shell installed automatically)
docker build -f src/main/docker/Dockerfile.jvm \
  -t quay.io/your-org/causa-backend:latest .

docker push quay.io/your-org/causa-backend:latest
```

#### Deployment Steps

1. Create secret for API key:
```bash
oc create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=your-bob-api-key \
  -n diagnostics-tool
```

2. Update deployment to use secret:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: causa-backend
  namespace: diagnostics-tool
spec:
  template:
    spec:
      containers:
      - name: causa-backend
        env:
        - name: LLM_API_KEY
          valueFrom:
            secretKeyRef:
              name: causa-llm-secrets
              key: LLM_API_KEY
        - name: LLM_PROVIDER
          value: "bob"
```

3. Deploy:
```bash
oc apply -k deployment/kubernetes/overlays/openshift
```

**Note:** The same `LLM_API_KEY` secret is used for all LLM providers (Claude, Vertex AI, BOB Shell). Only the key value is provider-specific.

## Monitoring

### Health Checks

BOB Shell availability is checked via health endpoint:

```bash
curl http://localhost:8080/q/health
```

Response:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "BOB Shell Readiness",
      "status": "UP",
      "data": {
        "provider": "bob",
        "shell_path": "bob",
        "version": "1.0.4"
      }
    }
  ]
}
```

### Logs

BOB Shell integration logs key events:

```
INFO  [com.causa.llm.BobShellPromptSender] BOB Shell is available and ready
INFO  [com.causa.llm.BobShellPromptSender] Sending prompt to BOB Shell
INFO  [com.causa.llm.BobShellPromptSender] Using stdin mode for large prompt (459000 chars)
INFO  [com.causa.llm.BobShellPromptSender] BOB Shell execution completed successfully
INFO  [com.causa.llm.BobShellPromptSender] Prompt sent successfully - tokens: 120000, latency: 116234ms
```

## Comparison with Other Providers

| Feature | BOB Shell | Anthropic (Claude) | Vertex AI |
|---------|-----------|-------------------|-----------|
| Installation | npm package | API only | GCP setup |
| Authentication | API key | API key | ADC/Service Account |
| Large Prompts | ✅ Stdin mode | ✅ Native | ✅ Native |
| Token Tracking | ✅ Extracted | ✅ Native | ✅ Native |
| Caching | ❌ Not supported | ✅ Prompt caching | ✅ Context caching |
| Cost | ~$0.01/1K tokens | ~$0.003/1K tokens | ~$0.003/1K tokens |

## Troubleshooting

### Debug Mode

Enable debug logging:

```yaml
quarkus:
  log:
    category:
      "com.causa.llm":
        level: DEBUG
```

### Verify BOB Shell Installation

```bash
# Check if BOB Shell is installed
which bob

# Check version
bob --version

# Test BOB Shell directly
bob --accept-license -p "Hello, BOB!" -o json
```

### Test Integration

```bash
# Check health endpoint
curl http://localhost:8080/q/health/ready

# Send test request (when API is available)
curl -X POST http://localhost:8080/api/diagnostics \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Test prompt"}'
```

## Support

For issues or questions:
- Check logs: `kubectl logs -f pod/causa-backend-xxx`
- Verify BOB Shell: `bob --version`
- Check API key: `echo $LLM_API_KEY`
- Review configuration: `cat application.yml`

## Installation Methods

BOB Shell can be installed using IBM's official installation script, which handles all dependencies and configuration automatically.

### Official Installation Script

**For macOS/Linux:**
```bash
curl -fsSL https://bob.ibm.com/download/bobshell.sh | bash
```

**For Windows:**
```powershell
powershell -ep Bypass 'irm -Uri "https://bob.ibm.com/download/bobshell.ps1" | iex'
```

### Docker Installation

The Dockerfile automatically uses the official installation script:
```dockerfile
# Install BOB Shell using official IBM script
RUN curl -fsSL https://bob.ibm.com/download/bobshell.sh | bash
```

No registry configuration or authentication tokens needed!

### Troubleshooting Installation

**Installation Script Fails:**
```
curl: (7) Failed to connect to bob.ibm.com
```

**Solution:**
1. Check internet connectivity
2. Verify firewall/proxy settings allow access to bob.ibm.com
3. Try from a different network
4. Contact IT support if corporate firewall blocks access

For detailed installation instructions and troubleshooting, see [BOB Shell Installation Guide](bob-shell-installation.md).

## Next Steps

1. **Implement Provider Switching**: Add logic to switch between LangChain and BOB Shell based on configuration
2. **Add Metrics**: Implement Prometheus metrics for BOB Shell usage
3. **Optimize Performance**: Fine-tune timeouts and concurrency limits
4. **Add Caching**: Implement response caching for repeated prompts