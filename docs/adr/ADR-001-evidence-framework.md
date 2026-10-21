# ADR-001: Evidence Framework for Validation UX

**Status:** Accepted  
**Date:** 2026-09-15  
**JIRA:** KRUIZE-241  
**Authors:** Causa Team  
**Reviewers:** Pinky Gupta, Kusuma Chalasani

---

## Context

Causa's dual validation system (PATH A: assertion-based + PATH B: rule-based) produces evidence from multiple sources (Kubernetes, Prometheus, pod logs, etc.) to validate RCA hypotheses. Currently, this evidence is embedded within validation results but not exposed in a structured, user-friendly format.

### Problems with Current Approach:

1. **No Unified Evidence Model**: Evidence from PATH A and PATH B use different structures
2. **Poor UX**: Users can't quickly see "what proves this diagnosis"
3. **Missing Evidence Not Visible**: When expected data is unavailable (e.g., metrics expired), users don't know
4. **No Prioritization**: All evidence treated equally, no concept of "key evidence"
5. **Verbose API**: Would need to send entire validation results to show evidence

### User Needs:

- **DevOps on-call**: "Show me the top 3 facts that prove this is an OOM kill"
- **Developer**: "What data is missing? How confident should I be?"
- **SRE**: "Give me the complete evidence trail for post-mortem documentation"

---

## Decision

We will implement a **unified Evidence Framework** with two core models:

### 1. EvidenceItem (Individual Evidence)

Represents a single piece of evidence with:
- **Source**: Which MCP server or system provided it (KUBERNETES_MCP, PROMETHEUS_MCP, CRYOSTAT_MCP, RULE_ENGINE, etc.)
- **Type**: What kind of data (CONTAINER_STATUS, METRIC, JVM_ANALYSIS, etc.)
- **Strength**: How reliable (DEFINITIVE, STRONG, MODERATE, WEAK)
- **Relationship**: How it relates to hypothesis (SUPPORTS, REFUTES, CONFLICTS)
- **Availability**: Was it found? (AVAILABLE, MISSING, UNAVAILABLE)
- **Content**: The actual data (rawSnippet) and why it matters (reasoning)
- **Metadata**: Detailed origin (query, pod, rule ID, etc.)

### 2. EvidenceCollection (Grouped Evidence)

Groups evidence for storage and full evidence API:
- **keyEvidence**: Top 3-5 most important items (for main API response)
- **supporting**: All supporting evidence
- **missing**: Expected but not found
- **refuting**: Contradicting evidence
- **conflicting**: Evidence with conflicts
- **summary**: Counts and aggregate confidence

### API Integration:

**Main Diagnostic API** (`GET /diagnostics/{id}`):
- Include **ONLY `keyEvidence`** (top 3-5 items) - not full collection
- Keep API response lean for UI performance

**Full Evidence API** (future):
- Optional endpoint `GET /diagnostics/{id}/evidence` returns full `EvidenceCollection`

---

## Rationale

### Why Separate EvidenceItem and EvidenceCollection?

- **EvidenceItem**: Transport/domain model - minimal, self-contained
- **EvidenceCollection**: Aggregation model - for storage and full evidence API
- **Main API**: Only sends `keyEvidence` (List<EvidenceItem>) - lean response

### Why Not Nest Evidence in ValidatedRCA?

- **Separation of Concerns**: RCA validation ≠ evidence presentation
- **API Response Size**: ValidatedRCA is already large; nesting full evidence makes it huge
- **Flexibility**: Evidence can be fetched separately if needed
- **User Focus**: Users need key evidence at root level for quick access

### Why Include Missing Evidence?

- **Trust**: Users need to know what data wasn't available
- **Prevention**: Identifies gaps in observability (e.g., "increase Prometheus retention")
- **Confidence**: Lower confidence when key evidence is missing

### Why `keyEvidence` at Root Level?

- **UX Priority**: Top evidence is what users need first
- **API Performance**: Sending only 3-5 items instead of full collection
- **Progressive Disclosure**: Full evidence available via separate endpoint if needed

---

## Consequences

### Positive:

✅ **Better UX**: Users see top 3-5 key facts immediately  
✅ **Transparent**: Missing/conflicting evidence is visible  
✅ **Unified**: Same model for PATH A and PATH B evidence  
✅ **Extensible**: Easy to add new sources (Cryostat, Kruize)  
✅ **Traceable**: Full provenance via metadata field  
✅ **Lean API**: Main response stays performant (only key evidence)  
✅ **Storage Efficient**: Full collection stored once, key items extracted on demand

### Negative:

⚠️ **New Models**: Adds 2 new domain models (but removes duplication)  
⚠️ **Migration**: Existing diagnostics won't have evidence (backward compatible)  
⚠️ **Computation**: Key evidence selection adds processing (but cached in DB)

### Neutral:

➖ **Metadata Flexibility**: `Map<String, String>` is flexible but untyped  
➖ **Key Evidence Algorithm**: Selection logic needs tuning based on user feedback

---

## Alternatives Considered

### Alternative 1: Use Existing Evidence Model

**Approach:** Extend existing `Evidence` record instead of creating `EvidenceItem`

```java
public record Evidence(
    String source,
    EvidenceType type,
    String snippet,
    double relevanceScore,
    Optional<String> structuredData,
    // Add new fields:
    EvidenceStrength strength,
    EvidenceRelationship relationship,
    EvidenceAvailability availability
) {}
```

**Rejected Because:**
- ❌ Breaks existing code using `Evidence`
- ❌ Field names don't align (e.g., `snippet` vs `rawSnippet`, `relevanceScore` vs `confidence`)
- ❌ Missing key fields (priority, reasoning, metadata)

### Alternative 2: Nest Evidence in ValidatedRCA

**Approach:** Add `EvidenceCollection` as field in `ValidatedRCA`

```java
public record ValidatedRCA(
    RootCauseAnalysis originalRca,
    List<ValidationResult> validationResults,
    EvidenceCollection evidence,  // ← Nested here
    ...
) {}
```

**Rejected Because:**
- ❌ Makes API response too large (full collection in every response)
- ❌ ValidatedRCA is already complex (mixing validation + evidence presentation)
- ❌ Can't easily send "just key evidence" to UI

### Alternative 3: Use Finding Model

**Approach:** Create `Finding` model that wraps hypothesis + evidence

```java
public record Finding(
    String id,
    Hypothesis hypothesis,
    List<EvidenceItem> evidence,
    ...
) {}
```

**Rejected Because:**
- ❌ Duplicates `RootCauseAnalysis` (which already has hypothesis = `anomalyType`)
- ❌ Adds unnecessary abstraction layer
- ❌ Would need to store both RCA and Finding (duplication)

### Alternative 4: Compute Evidence On-Demand (No Storage)

**Approach:** Don't store `EvidenceCollection`, compute from `ValidatedRCA` on each API call

**Rejected Because:**
- ❌ Performance overhead on every API call
- ❌ Can't evolve evidence extraction without reprocessing all diagnostics
- ❌ Harder to debug (evidence changes with code changes)
- ✅ **Chosen Hybrid:** Store full collection, send only key evidence in main API

### Alternative 5: Send Full Collection in Main API

**Approach:** Include full `EvidenceCollection` in `DiagnosticDetailResponse`

**Rejected Because:**
- ❌ UI team rejected (too much data for main view)
- ❌ Users only need 3-5 key facts for quick assessment
- ❌ Increases API response size unnecessarily
- ✅ **Chosen:** Only `keyEvidence` in main API, full collection available separately

---

## Implementation Plan

### Phase 1: Core Models (Week 1)
- [ ] Create `EvidenceItem.java` record
- [ ] Create `EvidenceCollection.java` record
- [ ] Add `evidenceCollection` JSON column to `DiagnosticEntity`
- [ ] Unit tests for models

### Phase 2: Evidence Extraction (Week 2)
- [ ] Create `EvidenceCollectionBuilder` service
- [ ] Extract evidence from PATH A (`ValidationResult`)
- [ ] Extract evidence from PATH B (`HypothesisValidationResult`)
- [ ] Implement key evidence selection algorithm
- [ ] Integration tests

### Phase 3: API Integration (Week 3)
- [ ] Update `DiagnosticDetailResponse` to include `keyEvidence`
- [ ] Persist `EvidenceCollection` in validation flow
- [ ] API tests
- [ ] Update API documentation

### Phase 4: Full Evidence API (Optional - Week 4)
- [ ] Create `GET /diagnostics/{id}/evidence` endpoint
- [ ] Return full `EvidenceCollection`
- [ ] API tests

---

## Metrics for Success

1. **User Feedback**: Positive feedback on evidence clarity from 5 users
2. **API Performance**: Main API response time < 200ms (95th percentile)
3. **Evidence Coverage**: >90% of diagnostics have 3+ key evidence items
4. **Missing Evidence Tracking**: Identify top 3 missing data sources for observability improvements

---

## Related Decisions

- **ADR-002** (future): Evidence ranking algorithm refinement
- **ADR-003** (future): Timeline/chart generation from evidence
- **Design Doc**: `docs/design/evidence-framework-design.md`

---

## References

- **JIRA**: KRUIZE-241 - Design and Implement Evidence Framework for Validation UX
- **Code**:
  - `com.causa.core.domain.validation.EvidenceItem`
  - `com.causa.core.domain.validation.EvidenceCollection`
  - `com.causa.core.domain.validation.Evidence` (existing)
  - `com.causa.core.domain.validation.ValidationResult` (existing)
  - `com.causa.core.services.rules.HypothesisValidationResult` (existing)

---

**Status:** ✅ Accepted  
**Date Accepted:** 2026-09-15  
**Next Review:** 2026-10-15 (after MVP implementation)
