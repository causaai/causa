package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Assertion.AssertionSource;
import com.causa.core.domain.validation.Assertion.AssertionType;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATH A turns what the LLM quoted while checking an assertion into evidence a reader can
 * verify, so the tests are about what survives the trip: the quote itself, the server it came
 * from, and the narration around it.
 */
class PathAEvidenceMapperTest {

    private static final RcaFinding FINDING =
        new RcaFinding("diag_123", AnomalyType.OOM_KILLED, "Container OOMKilled");

    private final PathAEvidenceMapper mapper = new PathAEvidenceMapper(sourceResolver());

    @Test
    void carriesTheQuoteItsSourceAndItsNarrationThrough() {
        Evidence evidence = Evidence.of(
            "POD_STATUS",
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
        assertThat(item.confidence()).isEqualTo(0.97);
    }

    /**
     * The LLM echoes back whatever section header it read, including the sequence suffix the
     * context stitcher adds. A reader needs the server, not the header.
     */
    @Test
    void resolvesTheEchoedSectionLabelToAnMcpServer() {
        EvidenceItem item = onlyItem(supported(quoting("POD_LOGS - sequence 124")));

        assertThat(item.source()).isEqualTo("kubernetes");
    }

    /** The id is the only thing tying an item back to the assertion it was quoted for. */
    @Test
    void namesTheItemAfterTheAssertionItWasQuotedFor() {
        assertThat(onlyItem(supported(quoting("POD_STATUS"))).id()).isEqualTo("pathA.a1.ev-01");
    }

    /**
     * The mapper sets no priority, so every item must carry the flat default rather than the
     * 0 an uninitialised int would give it — ranking is not designed yet, and 0 would read as
     * a decision that these items sort above everything.
     */
    @Test
    void leavesEveryItemAtTheFlatDefaultPriority() {
        assertThat(onlyItem(supported(quoting("POD_STATUS"))).priority())
            .isEqualTo(EvidenceConstants.DEFAULT_PRIORITY);
    }

    /**
     * Refuting evidence is the reason the pipeline exists — a diagnosis that only reports what
     * agrees with it is a diagnosis nobody can check.
     */
    @Test
    void keepsRefutingEvidenceAndNumbersItAfterTheSupporting() {
        ValidationResult result = ValidationResult.partiallySupported(
            assertion(), 0.5,
            List.of(quoting("POD_STATUS")),
            List.of(quoting("POD_EVENTS")),
            "mixed"
        );

        List<EvidenceItem> items = mapper.map(FINDING, result);

        assertThat(items).extracting(EvidenceItem::id)
            .containsExactly("pathA.a1.ev-01", "pathA.a1.ev-02");
        assertThat(items).extracting(EvidenceItem::source)
            .containsExactly("kubernetes", "kubernetes");
    }

    /**
     * An empty section quoted as proof is the LLM citing a gap. Dropped outright — a diagnosis
     * does not report its blind spots.
     */
    @Test
    void dropsEvidenceThatQuotesAnEmptySection() {
        Evidence empty = Evidence.of(
            "GC_ANALYSIS",
            Evidence.EvidenceType.GC_ANALYSIS,
            "GC_ANALYSIS: No Data Available",
            0.9
        );

        assertThat(mapper.map(FINDING, supported(empty))).isEmpty();
    }

    /**
     * Relevance is the only thing deciding strength, so the band edges are the contract between
     * what the LLM scored and how strongly the item reads.
     */
    @Test
    void banksRelevanceIntoStrength() {
        assertThat(strengthAt(0.95)).isEqualTo(EvidenceStrength.DEFINITIVE);
        assertThat(strengthAt(0.85)).isEqualTo(EvidenceStrength.STRONG);
        assertThat(strengthAt(0.65)).isEqualTo(EvidenceStrength.MODERATE);
        assertThat(strengthAt(0.40)).isEqualTo(EvidenceStrength.WEAK);
        assertThat(strengthAt(0.39)).isEqualTo(EvidenceStrength.CIRCUMSTANTIAL);
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
            "POD_STATUS", Evidence.EvidenceType.KUBERNETES_EVENT, "Exit Code: 137", 0.9);

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

    /** A registry declaring just the sections these tests quote. */
    private static McpSourceResolver sourceResolver() {
        McpRegistry registry = new McpRegistry(null);
        registry.init(new McpSettings(Map.of(
            "kubernetes", serverDeclaring("POD_STATUS", "POD_EVENTS", "POD_LOGS"),
            "cryostat", serverDeclaring("GC_ANALYSIS"))));
        return new McpSourceResolver(registry);
    }

    private static McpSettings.ServerConfig serverDeclaring(String... contextKeys) {
        return new McpSettings.ServerConfig(
            "streamable-http", "http://example:8080/mcp", Map.of(), false,
            new McpSettings.HealthCheckConfig("http://example:8080/healthz", 5000),
            5000, Map.of(), null,
            List.of(contextKeys).stream()
                .map(key -> new McpSettings.ToolConfig("a_tool", key, null, Map.of()))
                .toList());
    }

    private static ValidationResult supported(Evidence evidence) {
        return ValidationResult.supported(assertion(), 0.9, List.of(evidence), "because");
    }

    private static Evidence quoting(String source) {
        return Evidence.of(source, Evidence.EvidenceType.KUBERNETES_EVENT, "Exit Code: 137",
            "exited 137", "SIGKILL", 0.9);
    }

    private static Evidence scoring(double relevance) {
        return Evidence.of("POD_STATUS", Evidence.EvidenceType.KUBERNETES_EVENT,
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
