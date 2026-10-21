# Evidence Framework Design

---

## 1. Problem Statement

### Current State
**Evidence from RCA Pipeline:**
- Evidence extracted by LLM during RCA generation
- Returned as simple strings in `evidences` field
- No structure, no source tracking, limited validation (LLM's own judgment)

---

### Proposed State
**Evidence from Validation Pipeline:**
- Evidence extracted from structured validation (PATH A + PATH B)
- Returned as structured objects
- Source tracking, reliability signals, user-friendly

```json
{
  "evidences": [
    {
      "supportingEvidence": "Container exit code 137",
      "explanation": "Killed by Linux OOM killer",
      "source": "Kubernetes",
      "rawSnippet": "Exit Code: 137",
      "reliability": "DEFINITIVE"
    }
  ]
}
```


---

## 2. Two-Model Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    INTERNAL MODEL                           │
│                    EvidenceItem                             │
│                    12 fields                                │
│         (complete metadata for storage)                     │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   │ Store in DB
                   ▼
            ┌──────────────┐
            │   Database   │
            │  allEvidence │
            └──────┬───────┘
                   │
                   │ Select Top 3-5 + Transform
                   ▼
┌─────────────────────────────────────────────────────────────┐
│                      UI MODEL                               │
│                      Evidence                               │
│                      5 fields                               │
│            (user-friendly for API)                          │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   │ Return in API
                   ▼
            DiagnosisInfo.evidences
```

**Principle:**
- **Store complete:** `EvidenceItem` (12 fields)
- **Show minimal:** `Evidence` (5 fields)

---

## 3. Models

### 3.1 EvidenceItem (Internal - Complete)

**Purpose:** Full evidence model for storage and debugging

**Location:** `com.causa.core.domain.validation.EvidenceItem`

```java
public record EvidenceItem(
    String id,
    EvidenceSource source,
    EvidenceType type,
    EvidenceStrength strength,
    EvidenceRelationship relationship,
    EvidenceAvailability availability,
    String rawSnippet,
    String reasoning,
    double confidence,
    int priority,
    Instant collectedAt,
    Map<String, String> metadata
) {
    public enum EvidenceSource {
        KUBERNETES_MCP,
        PROMETHEUS_MCP,
        KRUIZE_MCP,
        CRYOSTAT_MCP,
        KUBERNETES_API,
        PROMETHEUS_API,
        QUARKUS_MCP,
        UNKNOWN
    }
    
    public enum EvidenceType {
        KUBERNETES_EVENT,
        CONTAINER_STATUS,
        POD_STATUS,
        METRIC,
        TIME_SERIES,
        LOG_PATTERN,
        LOG_ENTRY,
        JVM_ANALYSIS,
        GC_ANALYSIS,
        MEMORY_ANALYSIS,
        THREAD_ANALYSIS,
        HEAP_DUMP_ANALYSIS,
        RECOMMENDATION,
        OPTIMIZATION_SUGGESTION,
        OTHER
    }
    
    public enum EvidenceStrength {
        DEFINITIVE,
        STRONG,
        MODERATE,
        WEAK,
        CIRCUMSTANTIAL
    }
    
    public enum EvidenceRelationship {
        SUPPORTS,
        REFUTES,
        NEUTRAL,
        CONFLICTS
    }
    
    public enum EvidenceAvailability {
        AVAILABLE,
        MISSING,
        UNAVAILABLE,
        EXPIRED,
        INSUFFICIENT
    }
}
```

---

### 3.2 Evidence (UI - Simplified)

**Purpose:** User-friendly evidence for API

**Location:** `com.causa.api.dto.response.DiagnosticDetailResponse.Evidence`

```java
public record Evidence(
    @JsonProperty("supportingEvidence") String supportingEvidence,
    @JsonProperty("explanation")  String explanation,
    @JsonProperty("source")       String source,
    @JsonProperty("rawSnippet")   String rawSnippet,
    @JsonProperty("reliability")  String reliability
) {}
```

**Just 5 fields**

---

## 4. API Integration

### 4.1 Current DiagnosisInfo
```java
public record DiagnosisInfo(
    @JsonProperty("issue_title")    String issueTitle,
    @JsonProperty("anomaly_type")   String anomalyType,
    @JsonProperty("root_cause")     String rootCause,
    @JsonProperty("evidences")      List<String> evidences,  // ← Current: strings
    ...
) {}
```

---

### 4.2 Proposed DiagnosisInfo
```java
public record DiagnosisInfo(
    @JsonProperty("issue_title")    String issueTitle,
    @JsonProperty("anomaly_type")   String anomalyType,
    @JsonProperty("root_cause")     String rootCause,
    @JsonProperty("evidences")      List<Evidence> evidences,  // ← New: structured
    ...
) {}
```

**change:** Replace `List<String>` with `List<Evidence>`

---

### 4.3 API Response Example

```json
{
  "id": "diag-123",
  "diagnosis": {
    "issue_title": "OOM Kill Detected",
    "anomaly_type": "OOM_KILL",
    "root_cause": "Heap memory exhaustion",
    
    "evidences": [
      {
        "supportingEvidence": "Container exit code 137",
        "explanation": "Killed by Linux OOM killer",
        "source": "Kubernetes",
        "rawSnippet": "Exit Code: 137",
        "reliability": "DEFINITIVE"
      },
      {
        "supportingEvidence": "Heap usage 97% (500MB/512MB)",
        "explanation": "Severe memory pressure before crash",
        "source": "Prometheus",
        "rawSnippet": "jvm_memory_used_bytes{area=\"heap\"} 524288000",
        "reliability": "STRONG"
      },
      {
        "supportingEvidence": "GC pressure above 80%",
        "explanation": "JVM struggling to free memory",
        "source": "JVM Metrics",
        "rawSnippet": "jvm_gc_pause_seconds_sum 45.2",
        "reliability": "STRONG"
      }
    ],
    
    "rca_confidence_score": 0.87
  }
}
```

---

## 5. Storage

### DiagnosticEntity
```java
@Entity
public class DiagnosticEntity {
    @Column(columnDefinition = "TEXT")
    private String rcaResult;
    
    @Column(columnDefinition = "TEXT")
    private String validationResult;
    
    @Column(columnDefinition = "TEXT")
    private String allEvidence;  // JSON: List<EvidenceItem>
}
```

**Store:** Complete `EvidenceItem` model (12 fields)

---

## 6. Transformation

### Runtime Transform

```
Database: List<EvidenceItem>
              ↓
Select top 3-5 by priority
              ↓
Transform: EvidenceItem → Evidence
              ↓
API: List<Evidence>
```

### Source Mapping
```java
KUBERNETES_MCP → "Kubernetes"
PROMETHEUS_MCP → "Prometheus"
CRYOSTAT_MCP   → "Cryostat"
KRUIZE_MCP     → "Kruize"
RULE_ENGINE    → "Validation Rules"
LLM_EXTRACTION → "AI Analysis"
```

---

## 7. Selection Logic -- TBD (next sprint plan item)

**Priority (top 3-5):**
1. DEFINITIVE + SUPPORTS + AVAILABLE (max 3)
2. STRONG + SUPPORTS + AVAILABLE (fill to 5)
3. MISSING with DEFINITIVE/STRONG (max 1)
4. REFUTES if any

**Sort:** priority ASC, confidence DESC

---

## 8. Examples

### Internal (EvidenceItem)
```json
{
  "id": "oom.required.exit_code_137",
  "source": "KUBERNETES_MCP",
  "type": "CONTAINER_STATUS",
  "strength": "DEFINITIVE",
  "relationship": "SUPPORTS",
  "availability": "AVAILABLE",
  "rawSnippet": "Exit Code: 137",
  "reasoning": "Container killed by OOM killer",
  "confidence": 1.0,
  "priority": 1,
  "metadata": {
    "validationPath": "PATH_B",
    "ruleId": "oom.required.exit_code_137"
  }
}
```

### API (Evidence)
```json
{
  "supportingEvidence": "Exit Code: 137",
  "explanation": "Container killed by OOM killer",
  "source": "Kubernetes",
  "rawSnippet": "Exit Code: 137",
  "reliability": "DEFINITIVE"
}
```

---
