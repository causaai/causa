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

**Local Development:**
```bash
npm install -g bob-shell@1.0.4
```

**Docker/OpenShift:**
The Dockerfile automatically installs BOB Shell during image build.

### 2. API Key Configuration

Set the `BOBSHELL_API_KEY` environment variable:

```bash
export BOBSHELL_API_KEY=your-api-key-here
```

## Configuration

### application.yml

```yaml
causa:
  llm:
    provider: bob-shell  # Set to use BOB Shell
    timeout-seconds: 180  # BOB Shell timeout
    bob:
      shell-path: bob  # Path to BOB Shell executable (default: "bob")
      api-key: ${BOBSHELL_API_KEY:}  # API key (or use env var)
      timeout-seconds: 180  # BOB-specific timeout
```

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `BOBSHELL_API_KEY` | BOB Shell API key for authentication | Yes | - |
| `BOB_SHELL_PATH` | Path to BOB Shell executable | No | `bob` |
| `BOB_TIMEOUT_SECONDS` | Timeout for BOB Shell execution | No | `180` |
| `LLM_PROVIDER` | LLM provider to use | Yes | - |

### Kubernetes ConfigMap

For Kubernetes deployments, BOB Shell configuration can be set in the ConfigMap (`deployment/kubernetes/base/configmap.yaml`):

```yaml
# BOB Shell Configuration (Public settings)
# Path to BOB Shell executable (default: 'bob' assumes it's in PATH)
BOB_SHELL_PATH: "bob"
BOB_TIMEOUT_SECONDS: "180"
```

**Note:** The `BOBSHELL_API_KEY` should be stored in a Kubernetes Secret, not in the ConfigMap, as it contains sensitive authentication credentials.

## Usage

### Direct Usage (When Switching Logic is Implemented)

Once the provider switching logic is implemented by your team, you can use BOB Shell by:

1. Setting the provider in configuration:
```yaml
causa:
  llm:
    provider: bob-shell
```

2. Or via environment variable:
```bash
export LLM_PROVIDER=bob-shell
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
Solution: Install BOB Shell via npm install -g bob-shell@1.0.4
```

**2. API Key Missing**
```
Error: BOBSHELL_API_KEY environment variable not set
Solution: Set the API key: export BOBSHELL_API_KEY=your-key
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

1. Install BOB Shell:
```bash
npm install -g bob-shell@1.0.4
```

2. Set API key:
```bash
export BOBSHELL_API_KEY=your-api-key
```

3. Run causa-backend:
```bash
./mvnw quarkus:dev
```

### Docker Build

```bash
# Build the application
./mvnw package

# Build Docker image (BOB Shell installed automatically)
docker build -f src/main/docker/Dockerfile.jvm -t causa-backend:latest .

# Run with API key
docker run -e BOBSHELL_API_KEY=your-api-key -p 8080:8080 causa-backend:latest
```

### OpenShift Deployment

1. Create secret for API key:
```bash
oc create secret generic bob-shell-secret \
  --from-literal=BOBSHELL_API_KEY=your-api-key
```

2. Update deployment to use secret:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: causa-backend
spec:
  template:
    spec:
      containers:
      - name: causa-backend
        env:
        - name: BOBSHELL_API_KEY
          valueFrom:
            secretKeyRef:
              name: bob-shell-secret
              key: BOBSHELL_API_KEY
        - name: LLM_PROVIDER
          value: "bob-shell"
```

3. Deploy:
```bash
oc apply -f deployment.yaml
```

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
        "provider": "bob-shell",
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
- Check API key: `echo $BOBSHELL_API_KEY`
- Review configuration: `cat application.yml`

## Next Steps

1. **Implement Provider Switching**: Add logic to switch between LangChain and BOB Shell based on configuration
2. **Add Metrics**: Implement Prometheus metrics for BOB Shell usage
3. **Optimize Performance**: Fine-tune timeouts and concurrency limits
4. **Add Caching**: Implement response caching for repeated prompts