# Evidence Ranking and Selection — Design Document

**Version:** 1.0  
**Date:** 2026-09-30  
**Status:** Proposed  

---

## Understanding the Current System

### Data Sources Available

**PATH A (Assertion-based - LLM validation):**
- `ValidationResult` objects containing:
  - `Evidence` with `relevanceScore` (0.0-1.0)
  - `Evidence.type` (KUBERNETES_EVENT, METRIC, etc.)
  - `Evidence.source` (kubernetes-events, prometheus-metrics, etc.)
  - `Assertion.type` (CAUSALITY, OBSERVATION, TREND, CONFIGURATION)
  - `ValidationResult.status` (SUPPORTED, PARTIALLY_SUPPORTED, UNKNOWN, UNSUPPORTED)

**PATH B (Rule-based validation):**
- `HypothesisValidationResult` containing:
  - `RuleEvaluationResult` with matched signals
  - `Rule.type` (REQUIRED, SUPPORTING, EXCLUSION)
  - `Signal.type` (METRIC, K8S_EVENT, LOG_PATTERN, etc.)
  - `Signal.weight` (scoring contribution)

**RCA Output:**
- `RootCauseAnalysis.rootCause` (text explaining what caused the issue)

**Current Response:**
- `DiagnosticDetailResponse.validationEvidences` is declared but currently `null`
- Comment says: `// TODO: Once validation pipeline is integrated, transform EvidenceItem -> Evidence`

**Storage:**
- `Diagnostic.validationData` stores all validation results as JSONB
- `EvidenceItem` has: `strength`, `confidence`, `priority`, `evidenceHypothesisAlignment`

---

## Proposed Solution: Two-Step Ranking

### Step 1: Strength-Based Bucketing

**Purpose:** Group all evidences by inherent strength (tier-based classification)

**Strength Tiers:**

| Tier | Score | Classification Criteria |
|------|-------|------------------------|
| **DEFINITIVE** | 1.0 | `KUBERNETES_EVENT` with OOMKilled/exit-137 **+** `REQUIRED` rule match |
| **STRONG** | 0.80 | `METRIC` with heap >95% **OR** relevanceScore ≥0.90 **OR** Multiple Full GCs |
| **MODERATE** | 0.55 | `MEMORY_ANALYSIS`/`GC_ANALYSIS` **OR** 0.70 ≤ relevanceScore <0.90 |
| **WEAK** | 0.30 | Keyword-match source **OR** 0.40 ≤ relevanceScore <0.70 |
| **CIRCUMSTANTIAL** | 0.10 | `UNSUPPORTED` assertion evidence **OR** relevanceScore <0.40 |

**Classification Logic:**

```
EvidenceStrengthClassifier.classify(Evidence e, ValidationResult vr):
  
  // DEFINITIVE: Direct proof
  if (e.type == KUBERNETES_EVENT && snippet.contains("OOMKilled" | "exit 137"))
     AND parentAssertion validated by REQUIRED rule:
      return DEFINITIVE
  
  // STRONG: High-confidence indicators  
  if (e.type == METRIC && (heap >95% | fullGC count >10))
     OR e.relevanceScore >= 0.90:
      return STRONG
  
  // MODERATE: Supporting evidence
  if (e.type in [MEMORY_ANALYSIS, GC_ANALYSIS, JVM_ANALYSIS])
     OR (0.70 <= e.relevanceScore < 0.90):
      return MODERATE
  
  // WEAK: Low confidence
  if (e.source == "keyword-match")
     OR (0.40 <= e.relevanceScore < 0.70):
      return WEAK
  
  // CIRCUMSTANTIAL: Contradicted or very weak
  if (vr.status == UNSUPPORTED)
     OR e.relevanceScore < 0.40:
      return CIRCUMSTANTIAL
```

**Example Bucketing:**

```
18 evidences → Bucketed:

DEFINITIVE (3):
  - ev1: "Exit code 137" (k8s-event, REQUIRED rule)
  - ev2: "Container OOMKilled" (k8s-event, REQUIRED rule)
  - ev3: "Heap -Xmx215m" (exact config, relevance 0.98)

STRONG (7):
  - ev4: "Memory limit 256Mi" (manifest, relevance 0.92)
  - ev5: "Heap 98% before crash" (metric, relevance 0.88)
  - ev6: "15 Full GC in 5min" (gc-analysis, relevance 0.85)
  - ev7: "Kruize: +128Mi" (recommendation, relevance 0.90)
  - ev8: "Container limit 256Mi" (k8s-event, relevance 0.91)
  - ev9: "Pod limit 256MiB" (manifest, relevance 0.89)
  - ev10: "Metaspace +45MB" (memory-analysis, relevance 0.87)

MODERATE (5):
  - ev11-ev15: Trends, partial metrics, etc.

WEAK (3):
  - ev16-ev18: Context info, low relevance
```

---

### Step 2: Intra-Bucket Deduplication + RCA Relevance Ranking

**Purpose:** Within each bucket, deduplicate semantically similar evidences and rank by RCA-specific relevance

**Process for Each Bucket:**

```
For each bucket (DEFINITIVE, STRONG, MODERATE, WEAK):
  
  1. DEDUPLICATION:
     - Identify evidences saying the same thing
     - Strategy: Information subsumption
     - Rule: If evidence B contains all info from A + more detail, keep B
  
  2. RCA ALIGNMENT SCORING:
     - Score each unique evidence (0.0-1.0) on how well it proves 
       specific claims in the RCA root cause
     - Consider: Does this PROVE a claim, or just show a symptom?
  
  3. SORT by RCA alignment score (descending)
```

---

### Deduplication Examples

**Example 1: Same information, different wording**

```
STRONG bucket before dedup:
  ev4: "Memory limit 256Mi (from manifest)"
  ev8: "Container memory limit 256Mi"
  ev9: "Pod memory limit set to 256MiB"

Analysis:
  - All state the same fact: limit is 256Mi
  - ev4 has most detail (mentions "from manifest")
  
Action:
  - Keep ev4
  - Mark ev8, ev9 as duplicates of ev4

STRONG bucket after dedup:
  ev4: "Memory limit 256Mi (from manifest)"
```

**Example 2: Subsumption (one contains the other)**

```
STRONG bucket:
  ev5: "Heap usage 98%"
  ev5b: "Heap usage 98% before crash at 14:32:15"

Analysis:
  - ev5b contains everything in ev5 plus more (timestamp, context)
  
Action:
  - Keep ev5b (more detailed)
  - Mark ev5 as duplicate of ev5b
```

---

### RCA Alignment Scoring

**Purpose:** Rank evidences by how well they prove specific RCA claims

**RCA Example:**
```
"The container is being OOMKilled because the JVM heap configuration 
(-Xmx215m) combined with non-heap memory overhead exceeds the 
container's 256Mi limit."
```

**RCA Claims Breakdown:**
1. Container is OOMKilled
2. JVM heap is -Xmx215m
3. Container limit is 256Mi
4. Non-heap overhead contributes to exceeding limit

**Scoring Example for STRONG Bucket:**

| Evidence | Text | Proves Claim | RCA Score |
|----------|------|--------------|-----------|
| ev4 | "Memory limit 256Mi (manifest)" | Claim #3 directly | **0.92** |
| ev10 | "Metaspace increased 45MB" | Claim #4 (non-heap) | **0.88** |
| ev5 | "Heap 98% before crash" | Symptom, not cause | **0.65** |
| ev6 | "15 Full GC in 5min" | Symptom, not cause | **0.60** |
| ev7 | "Kruize: increase +128Mi" | Recommendation, not evidence | **0.45** |

**Ranked STRONG Bucket:**
```
1. ev4 (0.92) - Proves limit claim
2. ev10 (0.88) - Proves non-heap overhead
3. ev5 (0.65) - Shows symptom
4. ev6 (0.60) - Shows symptom
5. ev7 (0.45) - Future action, not proof
```

---

### LLM Requirement for Intra-Bucket Ranking

**Why LLM is needed:**

1. **Semantic Deduplication:**
   - Must understand "256Mi" = "256MiB" = "256 Mi"
   - Must recognize "Container limit" = "Pod limit" = "Memory limit"
   - Programmatic fingerprinting fails on these

2. **RCA Claim Matching:**
   - Must understand "Metaspace" relates to "non-heap overhead"
   - Must distinguish between "proves a claim" vs "shows a symptom"
   - Must parse RCA text and match evidences to specific claims

**LLM Call Per Bucket:**

**When to call:**
- Only for buckets with >5 evidences
- Skip small buckets (use default relevanceScore order)

**Input:**
- Bucket evidences
- RCA root cause text
- Bucket strength tier

**Output:**
```json
{
  "evidences": [
    {
      "id": "ev4",
      "duplicate_of": null,
      "rca_alignment_score": 0.92,
      "proves_claim": "Container limit is 256Mi"
    },
    {
      "id": "ev8",
      "duplicate_of": "ev4",
      "rca_alignment_score": null,
      "dedup_reason": "Same as ev4, less detailed"
    },
    {
      "id": "ev10",
      "duplicate_of": null,
      "rca_alignment_score": 0.88,
      "proves_claim": "Non-heap overhead contributes"
    }
  ]
}
```

---

## Final Selection: Tier-Greedy Algorithm

**After all buckets are ranked, select top 5:**

```
Algorithm:
  result = []
  
  for bucket in [DEFINITIVE, STRONG, MODERATE, WEAK, CIRCUMSTANTIAL]:
    for evidence in bucket (already ranked):
      if len(result) < 5:
        result.append(evidence)
      else:
        return result
  
  return result
```

**Example:**

```
Input Buckets (after dedup + ranking):
  DEFINITIVE (3): [ev1, ev2, ev3]
  STRONG (4): [ev4, ev10, ev5, ev6]
  MODERATE (5): [ev11, ev12, ev13, ev14, ev15]

Selection:
  Take 3 from DEFINITIVE: [ev1, ev2, ev3]
  Take 2 from STRONG: [ev4, ev10]
  
Top 5: [ev1, ev2, ev3, ev4, ev10]
```

---

## Architecture

### Components

**1. EvidenceStrengthClassifier**
- Rule-based tier assignment
- Input: Evidence + ValidationResult
- Output: EvidenceStrength (DEFINITIVE...CIRCUMSTANTIAL)

**2. IntraBucketRanker**
- LLM-based dedup + RCA scoring
- Input: Bucket evidences + RCA text
- Output: Ranked, deduplicated list
- Only called for buckets with >5 evidences

**3. TierGreedySelector**
- Selection logic
- Input: All ranked buckets
- Output: Top 5 evidences

### Flow

```
All Evidences (PATH A + PATH B)
       ↓
┌──────────────────────────────────┐
│ EvidenceStrengthClassifier       │  NO LLM
│ → Assign tier to each evidence   │
└──────────────┬───────────────────┘
               ↓
        Buckets created
               ↓
┌──────────────────────────────────┐
│ IntraBucketRanker                │  LLM (if >5 evidences)
│                                  │
│ For each bucket:                 │
│ 1. Dedup (semantic)              │
│ 2. RCA alignment scoring         │
│ 3. Sort by RCA score             │
└──────────────┬───────────────────┘
               ↓
     Ranked buckets
               ↓
┌──────────────────────────────────┐
│ TierGreedySelector               │  NO LLM
│ → Select top 5 across buckets    │
└──────────────┬───────────────────┘
               ↓
        Top 5 evidences
```

---

## Integration with PR #195

**What PR #195 likely implemented:**
- Evidence → DiagnosticDetailResponse.Evidence mapping
- Basic relevanceScore sorting
- May not have strength bucketing or RCA alignment

**How to align:**

1. **If PR uses simple relevanceScore sort:**
   - Add `EvidenceStrengthClassifier` as enhancement
   - Add `IntraBucketRanker` for semantic dedup
   - Backward compatible (same Evidence schema)

2. **If PR has strength tiers:**
   - Add intra-bucket RCA alignment as Phase 2
   - Enhance deduplication with LLM

3. **New fields to add:**
   - `Diagnostic.rankedEvidences` (JSONB) - stores top 5
   - `Evidence.rankScore` (optional) - for client-side re-sort

---

## Implementation Phases

### Phase 1: Strength Bucketing (No LLM)
- Implement `EvidenceStrengthClassifier`
- Tier-greedy selection
- Basic deduplication (fingerprinting)
- **Goal:** Validate tier assignment logic

### Phase 2: Intra-Bucket Ranking (LLM)
- Implement `IntraBucketRanker`
- LLM semantic dedup + RCA scoring
- Only for buckets >5 evidences
- **Goal:** Production-ready ranking

---

## Cost Estimate

| Bucket | Evidences | LLM Call? | Tokens | Cost |
|--------|-----------|-----------|--------|------|
| DEFINITIVE | 3 | No | 0 | $0 |
| STRONG | 7 | Yes | ~1500 | $0.002 |
| MODERATE | 5 | No | 0 | $0 |
| WEAK | 3 | No | 0 | $0 |

**Per diagnostic:** 1 LLM call, $0.002-0.004  
**At 1000/day:** $2-4/day = $60-120/month

---

## Summary

**Two-Step Process:**
1. **Bucket by strength** (DEFINITIVE → WEAK) - Rule-based, no LLM
2. **Rank within bucket** - LLM dedup + RCA alignment, only if >5 evidences

**Key Benefits:**
- ✅ Highest-strength evidences always surface first
- ✅ Semantic deduplication removes noise
- ✅ RCA-aware ranking shows proofs, not just symptoms
- ✅ Adaptive cost (LLM only when needed)

**Recommendation:** Implement Phase 1 first (validate bucketing), then Phase 2 (add LLM)

---

**End of Design Document**
