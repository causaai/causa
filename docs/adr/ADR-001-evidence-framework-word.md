# Architecture Decision Record

## ADR-001: Evidence Framework for Validation UX

---

### Document Information

| Field | Value |
|-------|-------|
| **Status** | ✅ Accepted |
| **Date** | September 15, 2026 |
| **JIRA** | KRUIZE-241 |
| **Authors** | Causa Team |
| **Reviewers** | Pinky Gupta, Kusuma Chalasani |

---

## Executive Summary

This ADR documents the decision to implement a unified Evidence Framework for Causa's validation system. The framework provides structured, user-friendly evidence presentation for Root Cause Analysis (RCA) validation, supporting both assertion-based (PATH A) and rule-based (PATH B) validation approaches.

**Key Decision:** Expose only top 3-5 "key evidence" items in main API response, with full evidence collection available via separate endpoint.

---

## 1. Context and Problem Statement

### Current Situation

Causa's dual validation system produces evidence from multiple sources:
- Kubernetes Events and Container Status
- Prometheus Metrics
- Pod Logs
- JVM Analysis (Cryostat, GC logs)
- Kruize Recommendations

This evidence is currently embedded within validation results but not exposed in a structured format.

### Problems

1. **No Unified Evidence Model**
   - PATH A and PATH B use different evidence structures
   - No common representation across sources

2. **Poor User Experience**
   - Users can't quickly identify "what proves this diagnosis"
   - No prioritization of evidence
   - All evidence treated equally

3. **Missing Evidence Not Visible**
   - When expected data is unavailable (e.g., metrics expired after 7 days)
   - Users don't know what's missing or why

4. **Verbose API Responses**
   - Would need to send entire validation results to show evidence
   - Performance impact on frontend

### User Personas and Needs

**DevOps Engineer (On-Call)**
- *Need:* "Show me the top 3 facts that prove this is an OOM kill"
- *Scenario:* Paged at 2 AM, needs quick diagnosis

**Application Developer**
- *Need:* "What data is missing? How confident should I be in this diagnosis?"
- *Scenario:* Investigating recurring production crashes

**SRE (Post-Mortem)**
- *Need:* "Give me the complete evidence trail for documentation"
- *Scenario:* Writing incident report

---

## 2. Decision

### Core Decision

Implement a **unified Evidence Framework** with two core models:

#### 2.1 EvidenceItem (Individual Evidence)

Single piece of evidence with comprehensive metadata:

**Key Fields:**
- `source`: Which MCP server or system provided it (KUBERNETES_MCP, PROMETHEUS_MCP, CRYOSTAT_MCP, RULE_ENGINE, etc.)
- `type`: What kind of data (CONTAINER_STATUS, METRIC, JVM_ANALYSIS, LOG_PATTERN, etc.)
- `strength`: How reliable (DEFINITIVE, STRONG, MODERATE, WEAK, CIRCUMSTANTIAL)
- `relationship`: How it relates to hypothesis (SUPPORTS, REFUTES, CONFLICTS, NEUTRAL)
- `availability`: Was it found? (AVAILABLE, MISSING, UNAVAILABLE, EXPIRED)
- `rawSnippet`: The actual data/text
- `reasoning`: Why this evidence matters
- `confidence`: 0.0-1.0 relevance score
- `metadata`: Flexible key-value pairs (query, pod, rule ID, etc.)

#### 2.2 EvidenceCollection (Grouped Evidence)

Collection of evidence items grouped for different use cases:

**Key Lists:**
- `keyEvidence`: Top 3-5 most important items (for main API)
- `supporting`: All supporting evidence
- `missing`: Expected but not found
- `refuting`: Contradicting evidence
- `conflicting`: Evidence with internal conflicts
- `summary`: Counts and aggregate confidence metrics

### API Integration Strategy

**Main Diagnostic API (`GET /diagnostics/{id}`):**
- Include **ONLY `keyEvidence`** (List<EvidenceItem>) at root level
- Keep response lean (3-5 items instead of full collection)
- Fast load time for UI

**Full Evidence API (Optional Future Enhancement):**
- `GET /diagnostics/{id}/evidence`
- Returns complete `EvidenceCollection`
- For detailed investigation and post-mortem documentation

---

## 3. Detailed Rationale

### 3.1 Why Separate EvidenceItem and EvidenceCollection?

**EvidenceItem:**
- Minimal, self-contained domain model
- Can be used independently (transport, storage, API)
- Same structure for PATH A and PATH B

**EvidenceCollection:**
- Aggregation/grouping model
- For storage and full evidence API
- Not sent in entirety via main API

### 3.2 Why Not Nest Evidence in ValidatedRCA?

**Reasons:**
1. **Separation of Concerns**
   - RCA validation ≠ evidence presentation
   - ValidatedRCA already has complex validation results

2. **API Response Size**
   - ValidatedRCA is already large
   - Nesting full evidence collection would bloat response

3. **Flexibility**
   - Evidence can be fetched separately if needed
   - Progressive disclosure for UI

4. **User Focus**
   - Users need key evidence at root level for quick access
   - Not buried inside validation structure

### 3.3 Why Include Missing Evidence?

**Benefits:**

**Build Trust:**
- Users see what data wasn't available
- Transparent about limitations

**Prevent Future Issues:**
- Identifies gaps in observability
- Example: "Prometheus retention too short - increase to 30 days"

**Manage Confidence:**
- Lower confidence score when key evidence is missing
- Users make informed decisions

**Example:**
```
⚠️ Missing Evidence (1 item)

Memory Trend Data
└─ Would confirm continuous growth pattern
└─ Reason: Prometheus retention expired (7 days)
└─ Suggestion: Increase retention to 30 days
```

### 3.4 Why `keyEvidence` at Root Level?

**UX Priority:**
- Top evidence is what users need first
- Not buried in nested objects

**API Performance:**
- 3-5 items instead of 20+ full collection
- Faster JSON parsing
- Smaller network payload

**Progressive Disclosure:**
- Show key facts immediately
- Full evidence available via "View All" → separate API call

**Mobile-Friendly:**
- Small screens can't show 20 evidence items
- Key evidence fits in mobile viewport

---

## 4. Consequences

### 4.1 Positive Outcomes

✅ **Improved User Experience**
- Users see top 3-5 key facts immediately
- No information overload

✅ **Transparent System**
- Missing evidence visible
- Conflicting evidence shown
- Users understand limitations

✅ **Unified Model**
- Same structure for PATH A and PATH B
- Consistent across all evidence sources

✅ **Extensible**
- Easy to add new sources (Cryostat, Kruize, future tools)
- Metadata field handles source-specific details

✅ **Traceable**
- Full provenance via metadata
- Can trace evidence back to exact query/pod/rule

✅ **Performant API**
- Main response stays lean
- Only 3-5 key items sent

✅ **Storage Efficient**
- Full collection stored once in DB
- Key items extracted on demand

### 4.2 Negative Outcomes

⚠️ **New Domain Models**
- Adds 2 new models (but removes duplication)
- Learning curve for developers

⚠️ **Migration Consideration**
- Existing diagnostics won't have evidence
- Backward compatible (old diagnostics return empty evidence)

⚠️ **Additional Computation**
- Key evidence selection adds processing
- Mitigated by: caching in database after first computation

### 4.3 Neutral Outcomes

➖ **Metadata Flexibility**
- `Map<String, String>` is flexible but untyped
- Trade-off: extensibility vs type safety

➖ **Key Evidence Algorithm**
- Selection logic needs tuning based on user feedback
- Can evolve without changing model structure

---

## 5. Alternatives Considered

### Alternative 1: Extend Existing Evidence Model

**Approach:**
```java
public record Evidence(
    String source,
    EvidenceType type,
    String snippet,
    double relevanceScore,
    // Add new fields:
    EvidenceStrength strength,
    EvidenceRelationship relationship
) {}
```

**Why Rejected:**
- ❌ Breaks existing code using `Evidence`
- ❌ Field names don't align (e.g., `snippet` vs `rawSnippet`)
- ❌ Missing key fields (priority, reasoning, metadata)

---

### Alternative 2: Nest Evidence in ValidatedRCA

**Approach:**
```java
public record ValidatedRCA(
    RootCauseAnalysis originalRca,
    EvidenceCollection evidence,  // ← Nested
    ...
) {}
```

**Why Rejected:**
- ❌ Makes API response too large
- ❌ ValidatedRCA already complex
- ❌ Can't send "just key evidence" to UI
- ❌ Poor separation of concerns

---

### Alternative 3: Use Finding Model

**Approach:**
```java
public record Finding(
    String id,
    Hypothesis hypothesis,
    List<EvidenceItem> evidence
) {}
```

**Why Rejected:**
- ❌ Duplicates `RootCauseAnalysis.anomalyType` (hypothesis)
- ❌ Adds unnecessary abstraction
- ❌ Would need to store both RCA and Finding

---

### Alternative 4: Compute Evidence On-Demand

**Approach:**
Don't store `EvidenceCollection`, compute from `ValidatedRCA` on each API call

**Why Rejected:**
- ❌ Performance overhead on every request
- ❌ Can't evolve extraction logic without reprocessing all diagnostics
- ❌ Harder to debug (evidence changes with code changes)

**Chosen Hybrid:**
- ✅ Store full collection in DB
- ✅ Send only key evidence in main API
- ✅ Full collection available via separate endpoint

---

### Alternative 5: Send Full Collection in Main API

**Approach:**
Include full `EvidenceCollection` in every `DiagnosticDetailResponse`

**Why Rejected:**
- ❌ UI team rejected (too much data for main view)
- ❌ Users only need 3-5 facts for quick assessment
- ❌ Increases response size unnecessarily (20+ items vs 3-5)
- ❌ Slower API response times

**Chosen:**
- ✅ Only `keyEvidence` in main API
- ✅ Full collection available separately if needed

---

## 6. Implementation Plan

### Phase 1: Core Models (Week 1)

**Tasks:**
- Create `EvidenceItem.java` record with all enums
- Create `EvidenceCollection.java` record
- Add `evidenceCollection` TEXT column to `DiagnosticEntity`
- Unit tests for model validation

**Deliverables:**
- Domain models in `com.causa.core.domain.validation`
- Database migration script
- 100% test coverage for models

---

### Phase 2: Evidence Extraction (Week 2)

**Tasks:**
- Create `EvidenceCollectionBuilder` service
- Extract evidence from PATH A (`ValidationResult.supportingEvidence`)
- Extract evidence from PATH B (`HypothesisValidationResult.requiredResults`)
- Implement key evidence selection algorithm
- Integration tests with real validation results

**Deliverables:**
- `EvidenceCollectionBuilder.java`
- Key evidence selection logic
- Integration test suite

---

### Phase 3: API Integration (Week 3)

**Tasks:**
- Update `DiagnosticDetailResponse` to include `keyEvidence` field
- Persist `EvidenceCollection` JSON in validation flow
- Update API documentation (OpenAPI/Swagger)
- End-to-end API tests

**Deliverables:**
- Updated API response
- Persisted evidence in database
- API documentation
- Postman/API test collection

---

### Phase 4: Full Evidence API (Optional - Week 4)

**Tasks:**
- Create `GET /diagnostics/{id}/evidence` endpoint
- Return full `EvidenceCollection`
- API tests
- Rate limiting (optional)

**Deliverables:**
- New API endpoint
- API documentation
- Test coverage

---

## 7. Success Metrics

### Quantitative Metrics

1. **API Performance**
   - Main API response time < 200ms (95th percentile)
   - Target: No degradation from current baseline

2. **Evidence Coverage**
   - \>90% of diagnostics have 3+ key evidence items
   - <10% have missing critical evidence

3. **API Response Size**
   - Main response with `keyEvidence`: < 50KB
   - Compare to: Full collection would be ~200KB+

### Qualitative Metrics

1. **User Feedback**
   - Positive feedback on evidence clarity from 5+ users
   - Survey: "Evidence helps me understand the diagnosis" >4/5 rating

2. **Missing Evidence Tracking**
   - Identify top 3 missing data sources
   - Action plan to improve observability coverage

3. **Developer Experience**
   - New developers can add evidence extraction for new sources in <2 hours
   - Clear documentation and examples

---

## 8. Related Decisions

**Future ADRs:**
- **ADR-002**: Evidence ranking algorithm refinement
- **ADR-003**: Timeline/chart generation from evidence
- **ADR-004**: Real-time evidence collection from MCP servers

**Related Documents:**
- Design Doc: `docs/design/evidence-framework-design.md`
- JIRA Epic: KRUIZE-241

---

## 9. References

### Code References
- `com.causa.core.domain.validation.EvidenceItem` (new)
- `com.causa.core.domain.validation.EvidenceCollection` (new)
- `com.causa.core.domain.validation.Evidence` (existing)
- `com.causa.core.domain.validation.ValidationResult` (existing)
- `com.causa.core.services.rules.HypothesisValidationResult` (existing)

### External References
- JIRA: KRUIZE-241 - Design and Implement Evidence Framework for Validation UX
- GitHub PR: (to be created)
- Design Review Notes: (to be linked)

---

## 10. Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| **Architect** | Pinky Gupta | 2026-09-15 | ✅ Approved |
| **Tech Lead** | Kusuma Chalasani | 2026-09-15 | ✅ Approved |
| **Product Owner** | (Name) | 2026-09-15 | Pending |

---

## 11. Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-09-15 | Causa Team | Initial version - Decision accepted |

---

**Status:** ✅ **ACCEPTED**  
**Next Review:** 2026-10-15 (after MVP implementation)  
**Document Owner:** Causa Team
