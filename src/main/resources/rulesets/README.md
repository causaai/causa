# YAML-Based Rule Sets

This directory contains YAML-based rule set definitions for hypothesis validation.

## Why YAML Rule Sets?

- **Zero-code extensibility**: Add new hypotheses and rule sets without writing Java code.
- **Configuration-driven**: Validation logic is separated from application code, making the engine generic and reusable.
- **Hot reload**: Rule changes are picked up automatically (every 30s by default) without restarting the application.
- **Easy to maintain**: Business rules are visible in YAML instead of being scattered across Java classes.
- **Version control friendly**: Rules can be reviewed, diffed, audited, and rolled back like any other configuration.
- **Faster iteration**: Domain experts can tune thresholds, weights, and matching conditions without code changes.
- **Reusable matching engine**: The same engine supports Kubernetes events, metrics, logs, JVM analysis, Cryostat, Kruize recommendations, and future signal types.
- **Scalable**: Adding new RCA hypotheses (OOM, CPU throttling, Disk Pressure, Network issues, etc.) only requires adding a new YAML file.
- **Environment-specific customization**: Different rule sets can be maintained for development, production, or customer-specific deployments.
- **Reduced deployment risk**: Most validation updates become configuration changes instead of requiring new application builds and deployments.
- **Future-proof**: Enables continuous refinement of validation logic as new failure patterns and evidence sources are introduced.

## Creating a New Rule Set

Simply create a new `.yml` file in this directory. Example:

```yaml
hypothesis: YOUR_HYPOTHESIS_NAME
name: "Human Readable Name"
description: "What this rule set validates"

thresholds:
  minSupportedScore: 10
  minPartiallySupportedScore: 5

required:
  - id: your.rule.id
    description: "What this rule checks"
    weight: 1
    match:
      signalType: METRIC
      signalName: some.metric.name
      condition: GREATER_THAN
      threshold: 0.90
    messages:
      success: "Rule passed message"
      failure: "Rule failed message"

supporting: []
exclusion: []
```

## Match Criteria

### Signal Type
- Single: `signalType: CONTAINER_STATUS`
- Multiple: `signalType: [KUBERNETES_EVENT, CONTAINER_STATUS]`

Available types:
- `KUBERNETES_EVENT`
- `POD_STATUS`
- `CONTAINER_STATUS`
- `METRIC`
- `LOG_PATTERN`
- `TRACE`
- `KRUIZE_RECOMMENDATION`
- `JVM_ANALYSIS`
- `CRYOSTAT_ANALYSIS`

### Signal Name
- Single: `signalName: exitCode`
- Multiple: `signalName: [terminationReason, reason]`

### Conditions

#### Equality
```yaml
signalValue: 137
condition: EQUALS
matchType: EXACT  # or CASE_INSENSITIVE
```

#### Numeric Comparison
```yaml
condition: GREATER_THAN
threshold: 0.90
```

Available: `GREATER_THAN`, `LESS_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL`

#### String Operations
```yaml
signalValue: OutOfMemoryError
condition: CONTAINS
matchType: CASE_INSENSITIVE
```

#### Regular Expression
```yaml
signalValue: "OOM.*Error"
condition: REGEX
```

## Match Types

- `EXACT`: Exact match (default)
- `CASE_INSENSITIVE`: Ignore case
- `CASE_SENSITIVE`: Enforce case

## Rule Weights

- **Required rules**: Typically `weight: 1` (gating conditions)
- **Supporting rules**: `weight: 1-10` (adds confidence)
- **Exclusion rules**: `weight: -1 to -10` (negative indicators)

## Hot-Reload Configuration

Configure in `application.yml`:

```yaml
causa:
  rules:
    hotreload:
      enabled: true    # Enable/disable hot-reload
      interval: 30s    # How often to check for changes
    dir: config/rulesets/  # External rules directory
```

Or via system properties:
```bash
-Dcausa.rules.hotreload.enabled=true
-Dcausa.rules.hotreload.interval=30s
-Dcausa.rules.dir=/etc/causa/rulesets
```

## External Rule Sets

You can also place YAML files in an external directory (default: `config/rulesets/`).
This is useful for:
- Environment-specific rules
- Production vs. development rules
- Customer-specific rules

External rules override classpath rules with the same hypothesis.

## Testing Your Rules

1. Create your YAML file
2. Save it
3. Wait 30 seconds (or trigger hot-reload)
4. Test with sample signals

Example test:
```bash
curl -X POST http://localhost:8080/api/v1/validation/test \
  -H "Content-Type: application/json" \
  -d '{
    "hypothesis": "YOUR_HYPOTHESIS_NAME",
    "signals": [...]
  }'
```

## Examples

See:
- `oom-killed.yml` - Complete OOMKilled validation
- `high-memory-pressure.yml` - Memory pressure without OOM

## Best Practices

1. **Use descriptive IDs**: `oom.required.exit_code_137` not `rule1`
2. **Clear messages**: Help operators understand what was checked
3. **Document thresholds**: Explain why you chose 0.90 vs 0.80
4. **Start conservative**: Add more supporting rules over time
5. **Test with real data**: Validate rules against actual incidents
6. **Version your rules**: Use metadata.version field

## Troubleshooting

**Rule set not loading?**
- Check YAML syntax
- Ensure `hypothesis` field is present
- Check logs for parsing errors

**Rules not matching?**
- Verify signal type/name spelling
- Check condition logic
- Test with simpler criteria first

**Hot-reload not working?**
- Verify `causa.rules.hotreload.enabled=true`
- Check file permissions
- Look for errors in logs
