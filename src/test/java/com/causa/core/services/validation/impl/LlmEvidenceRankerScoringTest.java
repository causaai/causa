package com.causa.core.services.validation.impl;

import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.domain.validation.ValidationEvidence.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Deterministic unit test for the code-side scoring the LLM could not be trusted to do.
 *
 * <p>The LLM returns only per-item relevance; {@link LlmEvidenceRanker#computeRanking} applies
 * {@code finalScore = 0.2 × strength + 0.8 × relevance}, sorts, and caps. Because that arithmetic
 * now lives in code it can be pinned exactly here — no model, no flakiness. This is the test that
 * proves strength actually influences the ranking.
 */
class LlmEvidenceRankerScoringTest {

    private static final int NO_CAP = 100;

    /** The formula itself: strength weighted 0.2, relevance 0.8. */
    @Test
    void finalScoreCombinesStrengthAndRelevance() {
        assertThat(LlmEvidenceRanker.finalScore(0.90, 1.00)).isCloseTo(0.98, within(1e-9));
        assertThat(LlmEvidenceRanker.finalScore(1.00, 0.25)).isCloseTo(0.40, within(1e-9));
        assertThat(LlmEvidenceRanker.finalScore(0.50, 0.50)).isCloseTo(0.50, within(1e-9));
    }

    /**
     * All three dimensions at once, at the 0.2/0.8 weights (relevance leads, strength refines):
     *   • dedup — "dup" was merged by the LLM, so it is absent from the map and must not appear;
     *   • relevance leads — "vh" (Very High) tops the list even with low strength, and "med" (Medium)
     *     stays last even at max strength: strength never vaults an item across a tier;
     *   • strength refines — within the High tier, "hiStrong" outranks "hiWeak" purely on strength.
     *
     * <pre>
     *   vh       s=0.3 r=1.00 → 0.2·0.3 + 0.8·1.00 = 0.86
     *   hiStrong s=0.9 r=0.75 → 0.2·0.9 + 0.8·0.75 = 0.78
     *   hiWeak   s=0.2 r=0.75 → 0.2·0.2 + 0.8·0.75 = 0.64
     *   med      s=1.0 r=0.50 → 0.2·1.0 + 0.8·0.50 = 0.60
     * </pre>
     */
    @Test
    void rankingHonoursBothStrengthAndRelevanceAfterDedup() {
        List<ValidationEvidence> evidence = List.of(
            ev("vh",       0.30),
            ev("hiStrong", 0.90),
            ev("hiWeak",   0.20),
            ev("med",      1.00),
            ev("dup",      0.80));  // merged by the LLM — never scored, must not appear

        Map<String, Double> relevance = new LinkedHashMap<>();
        relevance.put("vh", 1.00);
        relevance.put("hiStrong", 0.75);
        relevance.put("hiWeak", 0.75);
        relevance.put("med", 0.50);
        // "dup" deliberately omitted: this is how a merged duplicate reaches computeRanking.

        List<String> ranked = LlmEvidenceRanker.computeRanking(evidence, relevance, NO_CAP);

        // Relevance leads (vh first on low strength, med last on max strength) and strength refines
        // within the High tier (hiStrong above hiWeak). Both dimensions are provably at work.
        assertThat(ranked).containsExactly("vh", "hiStrong", "hiWeak", "med");
        assertThat(ranked).doesNotContain("dup");
    }

    /** Equal relevance → strength is the sole tiebreaker (the old TC-STR-001, now deterministic). */
    @Test
    void equalRelevanceIsBrokenByStrength() {
        List<ValidationEvidence> evidence = List.of(
            ev("sHi", 0.90),
            ev("sMid", 0.50),
            ev("sLo", 0.10));

        Map<String, Double> relevance = new LinkedHashMap<>();
        relevance.put("sLo", 0.50);   // insertion order deliberately reversed...
        relevance.put("sHi", 0.50);
        relevance.put("sMid", 0.50);

        List<String> ranked = LlmEvidenceRanker.computeRanking(evidence, relevance, NO_CAP);

        // ...so a pass can only come from strength ordering, not from input order leaking through.
        assertThat(ranked).containsExactly("sHi", "sMid", "sLo");
    }

    /** The top-N cap is enforced in code, keeping the highest-scoring items. */
    @Test
    void capKeepsTheTopScoringItems() {
        List<ValidationEvidence> evidence = List.of(
            ev("high", 0.90),
            ev("strong", 1.00),
            ev("mid", 0.50));

        Map<String, Double> relevance = new LinkedHashMap<>();
        relevance.put("high", 1.00);    // 0.2·0.90 + 0.8·1.00 = 0.98
        relevance.put("strong", 0.25);  // 0.2·1.00 + 0.8·0.25 = 0.40
        relevance.put("mid", 0.50);     // 0.2·0.50 + 0.8·0.50 = 0.50

        List<String> ranked = LlmEvidenceRanker.computeRanking(evidence, relevance, 2);

        assertThat(ranked).containsExactly("high", "mid");
    }

    /** A relevance score for an id that was never an input is ignored, not surfaced. */
    @Test
    void scoresForUnknownIdsAreDropped() {
        List<ValidationEvidence> evidence = List.of(ev("real", 0.50));

        Map<String, Double> relevance = new LinkedHashMap<>();
        relevance.put("real", 0.75);
        relevance.put("ghost", 1.00);  // no such input item

        List<String> ranked = LlmEvidenceRanker.computeRanking(evidence, relevance, NO_CAP);

        assertThat(ranked).containsExactly("real");
    }

    private static ValidationEvidence ev(String id, double strength) {
        return ValidationEvidence.of(id, "unit-test", EvidenceType.OTHER, "snippet for " + id, strength);
    }
}
