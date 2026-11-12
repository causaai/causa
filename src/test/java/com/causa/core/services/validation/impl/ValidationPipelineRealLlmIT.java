package com.causa.core.services.validation.impl;

import java.util.List;
import java.util.Optional;

import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.RootCauseAnalysis.ConfidenceSummary;
import com.causa.core.domain.RootCauseAnalysis.Recommendation;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.services.evidence.EvidenceCollectionResult;
import com.causa.core.services.evidence.EvidenceHarvester;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.core.services.validation.EvidenceRanker;
import com.causa.core.services.validation.RcaValidator;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the <b>entire validation pipeline</b>, driving the real
 * components — {@link RcaValidator} (PATH A assertion validation + PATH B rule engine),
 * {@link EvidenceHarvester}, and {@link EvidenceRanker} — against the real prompt and the real LLM
 * configured for this environment. It mirrors the exact orchestration
 * {@code DiagnosticServiceImpl} performs after an RCA is produced:
 *
 * <ol>
 *   <li>{@link RcaValidator#validate} — extract assertions, validate them (PATH A) and run the rule
 *       engine (PATH B), then aggregate into a dual verdict;</li>
 *   <li>select the {@link EvidenceHarvester} for the anomaly and {@link EvidenceHarvester#harvest}
 *       the evidence behind both paths;</li>
 *   <li>adapt the harvested items and {@link EvidenceRanker#rankAndDeduplicate} them.</li>
 * </ol>
 *
 * <p>Unlike {@link com.causa.core.services.evidence.impl.EvidenceRankingRealLlmIT} — which exercises
 * only the ranker in isolation — this test feeds a realistic OOM diagnostic context through the
 * whole chain, so a regression anywhere in validation, harvesting, or ranking surfaces here.
 *
 * <p><b>What it asserts.</b> The model is non-deterministic, so assertions check only stable,
 * documented outcomes: PATH A produced assertion results, PATH B produced a dual verdict, the
 * textbook OOM finding is validated (not refuted), evidence was harvested, and the ranked output is
 * non-empty and capped. Everything the pipeline produced is printed for eyeball verification.
 *
 * <p><b>How to run.</b> Needs a live, active LLM config (same {@code llm_configs} row the app reads)
 * and is gated behind a system property, skipped by default:
 *
 * <pre>{@code
 * ./mvnw test -Dcausa.real-llm-it=true \
 *     -Dtest=ValidationPipelineRealLlmIT \
 *     -Dquarkus.datasource.jdbc.url=... -Dquarkus.datasource.username=... -Dquarkus.datasource.password=...
 * }</pre>
 *
 * @since 0.0.1
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "causa.real-llm-it", matches = "true")
@DisplayName("Validation pipeline — real LLM (validate → harvest → rank)")
class ValidationPipelineRealLlmIT {

    /** Mirrors {@code LlmEvidenceRanker.MAX_RANKED_ITEMS} (private there) — the top-N cap. */
    private static final int MAX_RANKED = 20;

    private static final String DIAGNOSTIC_ID = "it-oom-pipeline-001";

    @Inject
    RcaValidator rcaValidator;

    @Inject
    EvidenceRanker evidenceRanker;

    @Inject
    Instance<EvidenceHarvester> evidenceHarvesters;

    @Test
    @DisplayName("Textbook OOM: PATH A + PATH B validate, evidence harvested, ranked end-to-end")
    void oomFindingFlowsThroughTheWholePipeline() {
        RootCauseAnalysis rca = oomRca();
        String context = oomDiagnosticContext();

        System.out.println("══════════════════════════════════════════════════════════");
        System.out.println("VALIDATION PIPELINE — end-to-end (OOM)");
        System.out.println("RCA root cause: " + rca.rootCause());

        // ---- Stage 1: validate (PATH A assertions + PATH B rules → dual verdict) ----
        ValidatedRCA validated = rcaValidator.validate(rca, context);
        assertThat(validated).as("validate() must return a result").isNotNull();

        System.out.println("── STAGE 1: VALIDATE ──");
        System.out.println("  assertion results (PATH A): " + validated.validationResults().size());
        validated.validationResults().forEach(vr ->
            System.out.println("    • " + vr.status() + " (conf " + String.format("%.2f", vr.confidence()) + ")"));

        // PATH A ran: assertions were extracted from the RCA and each has a verdict.
        assertThat(validated.validationResults())
            .as("PATH A must extract and validate at least one assertion from the RCA")
            .isNotEmpty();

        // PATH B ran: the rule engine produced a dual verdict.
        assertThat(validated.dualValidation())
            .as("PATH B (rule engine) must produce a dual validation verdict")
            .isNotNull();

        String finalStatus = validated.dualValidation().finalVerdict().status().name();
        double finalConfidence = validated.dualValidation().finalVerdict().confidence();
        System.out.println("  dual verdict (PATH A+B): " + finalStatus
            + " (conf " + String.format("%.2f", finalConfidence) + ")");

        // A textbook OOM finding against OOMKilled/exit-137/OutOfMemoryError context must not be
        // refuted. SUPPORTED is expected; PARTIALLY_SUPPORTED is tolerated for model variance.
        assertThat(finalStatus)
            .as("a textbook OOM finding must be validated, not refuted (status=%s)", finalStatus)
            .isIn("SUPPORTED", "PARTIALLY_SUPPORTED");

        // ---- Stage 2: harvest (read evidence behind both paths) ----
        RcaFinding finding = RcaFinding.from(DIAGNOSTIC_ID, rca);
        assertThat(finding).as("finding must be derivable from the RCA").isNotNull();

        EvidenceHarvester harvester = harvesterFor(finding.anomalyType());
        assertThat(harvester)
            .as("an evidence harvester must support anomaly %s", finding.anomalyType())
            .isNotNull();

        EvidenceCollectionResult harvest = harvester.harvest(finding, validated);
        assertThat(harvest).as("harvest() must return a result").isNotNull();

        System.out.println("── STAGE 2: HARVEST ──");
        System.out.println("  harvested items: " + harvest.totalCount()
            + " (refuting: " + harvest.refutingCount() + ")");
        System.out.println("  contributing sources: " + harvest.contributingSources());
        harvest.items().forEach(it ->
            System.out.println("    • [" + it.id() + "] (" + it.source() + ") "
                + truncate(firstNonBlank(it.statement(), it.rawSnippet()), 100)));

        assertThat(harvest.isEmpty())
            .as("the OOM validation produced evidence, so harvest must not be empty")
            .isFalse();

        // ---- Stage 3: rank (dedup + code-side scoring + top-N cap) ----
        List<ValidationEvidence> rankable = toRankableEvidence(harvest.items());
        assertThat(rankable)
            .as("harvested items with a usable snippet must adapt to rankable evidence")
            .isNotEmpty();

        RcaSummary summary = new RcaSummary(rca.issueSummary(), rca.rootCause());
        EvidenceRankingResponse ranked =
            evidenceRanker.rankAndDeduplicate(summary, rankable, finalStatus);

        System.out.println("── STAGE 3: RANK ──");
        System.out.println("  rankable in: " + rankable.size()
            + " → ranked out: " + ranked.rankedEvidenceIds().size()
            + "  (method: " + ranked.reasoning() + ")");
        for (int i = 0; i < ranked.rankedEvidenceIds().size(); i++) {
            System.out.println("    [" + (i + 1) + "] " + ranked.rankedEvidenceIds().get(i));
        }
        System.out.println("══════════════════════════════════════════════════════════");

        assertThat(ranked.rankedEvidenceIds())
            .as("ranking must return a non-empty ordered list")
            .isNotEmpty();
        assertThat(ranked.rankedEvidenceIds().size())
            .as("ranking must honour the top-%d cap", MAX_RANKED)
            .isLessThanOrEqualTo(MAX_RANKED);
        // Every ranked id must resolve back to a harvested item — the pipeline must not invent ids.
        List<String> rankableIds = rankable.stream().map(ValidationEvidence::id).toList();
        assertThat(rankableIds)
            .as("every ranked id must trace back to a harvested, rankable item")
            .containsAll(ranked.rankedEvidenceIds());
    }

    // -------------------------------------------------------------------------
    // Pipeline helpers — mirror DiagnosticServiceImpl's orchestration
    // -------------------------------------------------------------------------

    private EvidenceHarvester harvesterFor(AnomalyType anomalyType) {
        for (EvidenceHarvester candidate : evidenceHarvesters) {
            if (candidate.supports(anomalyType)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Adapts harvested {@link EvidenceItem}s into the {@link ValidationEvidence} the ranker consumes,
     * preserving each id — a faithful copy of {@code DiagnosticServiceImpl.toRankableEvidence}: items
     * without a usable snippet are dropped, and confidence is clamped to [0,1].
     */
    private static List<ValidationEvidence> toRankableEvidence(List<EvidenceItem> items) {
        return items.stream()
            .map(ValidationPipelineRealLlmIT::toRankable)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static ValidationEvidence toRankable(EvidenceItem item) {
        String snippet = (item.rawSnippet() != null && !item.rawSnippet().isBlank())
            ? item.rawSnippet() : item.statement();
        if (snippet == null || snippet.isBlank()) {
            return null;  // ValidationEvidence requires a non-blank snippet
        }
        String source = (item.source() != null && !item.source().isBlank()) ? item.source() : "unknown";
        double score = Math.max(0.0, Math.min(1.0, item.confidence()));
        return new ValidationEvidence(
            item.id(), source, ValidationEvidence.EvidenceType.OTHER,
            snippet, item.statement(), item.explanation(), score, Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Test fixtures — a textbook OOM finding + the context that supports it
    // -------------------------------------------------------------------------

    private static RootCauseAnalysis oomRca() {
        return new RootCauseAnalysis(
            "Container OOMKilled due to JVM heap exhaustion",
            "The causa-app container was terminated with OOMKilled (exit code 137) after the JVM heap "
                + "reached its -Xmx512m limit and threw java.lang.OutOfMemoryError.",
            "The pod restarted 4 times. Heap usage climbed to 98% of the configured maximum before an "
                + "OutOfMemoryError was thrown and the kubelet killed the container for exceeding its "
                + "512Mi memory limit.",
            "JVM heap (-Xmx512m) was exhausted; 15 Full GC events in 30s preceded the kill, and the "
                + "container exit code 137 with reason OOMKilled confirms the kubelet OOM termination.",
            AnomalyType.OOM_KILLED,
            "JVM heap exhaustion: the application allocated beyond the -Xmx512m heap limit, triggering "
                + "java.lang.OutOfMemoryError and an OOMKilled container termination (exit code 137).",
            List.of(
                "java.lang.OutOfMemoryError: Java heap space",
                "Reason: OOMKilled, Exit Code: 137"),
            List.of(
                "Heap usage reached 98% of max before termination",
                "15 Full GC events in the 30 seconds before the kill"),
            List.of(new Recommendation(
                "Root Cause Fix",
                "Increase heap/limit or fix the allocation hotspot",
                "Raise -Xmx and the container memory limit, or bound the cache that drives allocation.",
                "Review Cache.load allocation path before raising limits.",
                0.8,
                List.of())),
            new ConfidenceSummary(0.9, "High-confidence OOM: exit 137, OOMKilled, OutOfMemoryError all present."),
            "");
    }

    private static String oomDiagnosticContext() {
        return """
            --- POD STATUS ---
            Name: causa-app-7d9f8b6c4-xk2lp
            Namespace: causa-rca
            Phase: Running
            Reason: OOMKilled
            Exit Code: 137
            Restart Count: 4
            Last State: Terminated (reason: OOMKilled)

            --- CONTAINER RESOURCES ---
            Memory Limit: 512Mi
            Memory Request: 256Mi
            JVM Flags: -Xmx512m

            --- POD LOGS (recent) ---
            13:31:36 INFO  starting allocation round
            13:31:37 WARN  heap usage at 98% of max (218955776B)
            13:31:38 ERROR java.lang.OutOfMemoryError: Java heap space
            13:31:38 ERROR   at com.causa.app.Cache.load(Cache.java:88)
            13:31:39 INFO  15 Full GC events observed in the last 30 seconds

            --- KUBERNETES EVENTS ---
            Warning  OOMKilling  Container causa-app exceeded its memory limit (512Mi) and was OOMKilled
            Normal   Killing     Stopping container causa-app
            """;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
