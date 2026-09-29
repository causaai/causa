package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Assertion.AssertionSource;
import com.causa.core.domain.validation.Assertion.AssertionType;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.RcaFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATH A turns what the LLM quoted while checking an assertion into evidence a reader can
 * verify, so the tests are about what survives the trip: the quote itself, the server it came
 * from, and the narration around it.
 */
class PathAEvidenceMapperTest {

    private static final RcaFinding FINDING =
        new RcaFinding("diag_123", AnomalyType.OOM_KILLED, "Container OOMKilled");

    private final PathAEvidenceMapper mapper = new PathAEvidenceMapper();

    @Test
    void carriesTheQuoteItsSourceAndItsNarrationThrough() {
        Evidence evidence = Evidence.of(
            "POD STATUS",
            Evidence.EvidenceType.KUBERNETES_EVENT,
            "Exit Code: 137",
            "The container exited with 137.",
            "137 is SIGKILL, which the kernel sends on an OOM kill.",
            0.97
        );

        EvidenceItem item = onlyItem(supported(evidence));

        assertThat(item.rawSnippet()).isEqualTo("Exit Code: 137");
        assertThat(item.statement()).isEqualTo("The container exited with 137.");
        assertThat(item.explanation()).isEqualTo("137 is SIGKILL, which the kernel sends on an OOM kill.");
        assertThat(item.evidenceHypothesisAlignment()).isEqualTo(EvidenceHypothesisAlignment.SUPPORTS);
        assertThat(item.confidence()).isEqualTo(0.97);
    }

    /**
     * The LLM echoes back whatever section header it read, including the sequence suffix the
     * context stitcher adds. A reader needs the server, not the header.
     */
    @Test
    void resolvesTheEchoedSectionLabelToAnMcpServer() {
        EvidenceItem item = onlyItem(supported(quoting("POD LOGS (recent) - sequence 124")));

        assertThat(item.source()).isEqualTo("kubernetes");
        assertThat(item.metadata()).containsEntry(Metadata.RAW_SOURCE_LABEL, "POD LOGS (recent) - sequence 124");
    }

    @Test
    void attributesTheItemToItsFindingAndAssertion() {
        EvidenceItem item = onlyItem(supported(quoting("POD STATUS")));

        assertThat(item.id()).isEqualTo("pathA.a1.ev-01");
        assertThat(item.metadata())
            .containsEntry(Metadata.PATH, Metadata.PATH_A)
            .containsEntry(Metadata.FINDING_ID, "diag_123")
            .containsEntry(Metadata.ANOMALY_TYPE, "OOM_KILLED")
            .containsEntry(Metadata.ASSERTION_ID, "a1")
            .containsEntry(Metadata.ASSERTION_STATUS, "SUPPORTED");
    }

    /**
     * Refuting evidence is the reason the pipeline exists — a diagnosis that only reports what
     * agrees with it is a diagnosis nobody can check.
     */
    @Test
    void keepsRefutingEvidenceAndNumbersItAfterTheSupporting() {
        ValidationResult result = ValidationResult.partiallySupported(
            assertion(), 0.5,
            List.of(quoting("POD STATUS")),
            List.of(quoting("POD EVENTS")),
            "mixed"
        );

        List<EvidenceItem> items = mapper.map(FINDING, result);

        assertThat(items).extracting(EvidenceItem::evidenceHypothesisAlignment)
            .containsExactly(EvidenceHypothesisAlignment.SUPPORTS, EvidenceHypothesisAlignment.REFUTES);
        assertThat(items).extracting(EvidenceItem::id)
            .containsExactly("pathA.a1.ev-01", "pathA.a1.ev-02");
    }

    /**
     * An empty section quoted as proof is the LLM citing a gap. Dropped outright — a diagnosis
     * does not report its blind spots.
     */
    @Test
    void dropsEvidenceThatQuotesAnEmptySection() {
        Evidence empty = Evidence.of(
            "GC ANALYSIS (Cryostat JFR)",
            Evidence.EvidenceType.GC_ANALYSIS,
            "GC ANALYSIS (Cryostat JFR): No Data Available",
            0.9
        );

        assertThat(mapper.map(FINDING, supported(empty))).isEmpty();
    }

    /**
     * Relevance drives both strength and display order, so the band edges are the contract
     * between what the LLM scored and where the item lands in the panel.
     */
    @Test
    void banksRelevanceIntoStrengthAndPriority() {
        assertThat(strengthAt(0.95)).isEqualTo(EvidenceStrength.DEFINITIVE);
        assertThat(strengthAt(0.85)).isEqualTo(EvidenceStrength.STRONG);
        assertThat(strengthAt(0.65)).isEqualTo(EvidenceStrength.MODERATE);
        assertThat(strengthAt(0.40)).isEqualTo(EvidenceStrength.WEAK);
        assertThat(strengthAt(0.39)).isEqualTo(EvidenceStrength.CIRCUMSTANTIAL);

        assertThat(onlyItem(supported(scoring(0.95))).priority()).isEqualTo(1);
        assertThat(onlyItem(supported(scoring(0.85))).priority()).isEqualTo(2);
        assertThat(onlyItem(supported(scoring(0.65))).priority()).isEqualTo(3);
    }

    /** An unrecognised label is attributed to nothing rather than guessed at. */
    @Test
    void attributesAnUnrecognisedLabelToNoServer() {
        assertThat(onlyItem(supported(quoting("SOME SECTION WE DO NOT KNOW"))).source())
            .isEqualTo("unknown");
    }

    /**
     * The per-evidence explanation is what this quote contributed; the assertion-level one says
     * why the assertion landed where it did. The second is a fallback, never a substitute for
     * the assertion text itself — an item restating the claim would read as having settled it.
     */
    @Test
    void fallsBackToTheAssertionExplanationButNeverTheAssertionText() {
        Evidence unnarrated = Evidence.of(
            "POD STATUS", Evidence.EvidenceType.KUBERNETES_EVENT, "Exit Code: 137", 0.9);

        EvidenceItem item = onlyItem(
            ValidationResult.supported(assertion(), 0.9, List.of(unnarrated), "every signal agrees"));

        assertThat(item.explanation()).isEqualTo("every signal agrees");
        assertThat(item.statement()).isNull();
    }

    @Test
    void truncatesASnippetTooLongToStore() {
        String huge = "x".repeat(Snippet.MAX_LENGTH + 500);

        String stored = onlyItem(supported(Evidence.of(
            "POD LOGS (recent)", Evidence.EvidenceType.POD_LOG, huge, 0.9))).rawSnippet();

        assertThat(stored).hasSize(Snippet.MAX_LENGTH)
            .endsWith(Snippet.TRUNCATION_SUFFIX);
    }

    @Test
    void mapsNothingWhenThereIsNoValidationResult() {
        assertThat(mapper.map(FINDING, null)).isEmpty();
    }

    // --- helpers ---

    private static Assertion assertion() {
        return Assertion.of("a1", "Container was OOMKilled",
            AssertionType.OBSERVATION, AssertionSource.ROOT_CAUSE);
    }

    private static ValidationResult supported(Evidence evidence) {
        return ValidationResult.supported(assertion(), 0.9, List.of(evidence), "because");
    }

    private static Evidence quoting(String source) {
        return Evidence.of(source, Evidence.EvidenceType.KUBERNETES_EVENT, "Exit Code: 137",
            "exited 137", "SIGKILL", 0.9);
    }

    private static Evidence scoring(double relevance) {
        return Evidence.of("POD STATUS", Evidence.EvidenceType.KUBERNETES_EVENT,
            "Exit Code: 137", "exited 137", "SIGKILL", relevance);
    }

    private EvidenceItem onlyItem(ValidationResult result) {
        List<EvidenceItem> items = mapper.map(FINDING, result);
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private EvidenceStrength strengthAt(double relevance) {
        return onlyItem(supported(scoring(relevance))).strength();
    }
}
