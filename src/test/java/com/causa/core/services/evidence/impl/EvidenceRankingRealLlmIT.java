package com.causa.core.services.evidence.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;
import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.domain.validation.ValidationEvidence.EvidenceType;
import com.causa.core.services.validation.impl.LlmEvidenceRanker;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-LLM integration test for {@link LlmEvidenceRanker} that drives every case in
 * {@code docs/testing/relevance-dedup-test-cases.md} through the actual prompt and the actual
 * model configured for this environment.
 *
 * <p><b>Why this exists.</b> {@link LlmEvidenceRanker#rankAndDeduplicate} sends every non-empty set
 * through the real prompt + real LLM — there is no item-count shortcut — so calling it directly
 * exercises the deduplication and ranking behavior for each documented case, including the small
 * duplicate pairs where merging matters most.
 *
 * <p><b>What it asserts.</b> The model is non-deterministic, so assertions check only the stable,
 * documented outcomes (deduplication reduces/preserves count; the highest-relevance items land in
 * the top positions). For every case it prints <em>exactly what was sent to the ranker</em> (the
 * compact evidence lines, in the ranker's own {@code id: statement [strength: score]} format) and
 * <em>exactly what the ranker returned</em> (the ranked id order), so the observable outcomes in the
 * test doc can be verified by eye as well.
 *
 * <p><b>How to run.</b> It needs a live, active LLM config — the same one the app reads from the
 * {@code llm_configs} table (credentials are decrypted by the app). It is therefore gated behind a
 * system property and skipped by default:
 *
 * <pre>{@code
 * # point the datasource at a DB that holds an active llm_configs row, and supply the
 * # credential-decryption key the app uses, then:
 * ./mvnw test -Dcausa.real-llm-it=true \
 *     -Dtest=EvidenceRankingRealLlmIT \
 *     -Dquarkus.datasource.jdbc.url=... -Dquarkus.datasource.username=... -Dquarkus.datasource.password=...
 * }</pre>
 *
 * @since 0.0.1
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "causa.real-llm-it", matches = "true")
@DisplayName("Evidence Ranking — real LLM + real prompt (relevance-dedup-test-cases.md)")
class EvidenceRankingRealLlmIT {

    /** Strength is held constant so ranking is driven purely by LLM-assigned relevance. */
    private static final double FIXED_STRENGTH = 0.50;

    /** Mirrors {@code LlmEvidenceRanker.MAX_RANKED_ITEMS} (private there) — the top-N cap. */
    private static final int MAX_RANKED = 20;

    @Inject
    LlmEvidenceRanker ranker;

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void ranksAndDeduplicates(RankCase c) {
        // Mirrors the ranker's wire format exactly. NOTE: the "strength" the ranker consumes in
        // 0.2×strength + 0.8×relevance is the evidence's relevanceScore() field (a domain naming
        // quirk — relevanceScore() carries the assertion-level *strength*, not the RCA relevance the
        // LLM assigns). Here it is pinned to FIXED_STRENGTH so ordering is driven purely by relevance.
        String compact = c.evidence().stream()
            .map(ev -> String.format("%s: %s [strength: %.2f]", ev.id(), ev.statement(), ev.relevanceScore()))
            .collect(Collectors.joining("\n"));

        System.out.println("\n──────────────────────────────────────────────────────────");
        System.out.println("CASE: " + c.id() + " — " + c.title());
        System.out.println("RCA: " + c.rca().rootCause());
        System.out.println("Validation: " + c.validationStatus() + " (" + Math.round(c.validationScore() * 100) + "%)");
        System.out.println("── SENT TO RANKER (" + c.evidence().size() + " items) ──");
        System.out.println(compact);

        EvidenceRankingResponse response =
            ranker.rankAndDeduplicate(c.rca(), c.evidence(), c.validationScore(), c.validationStatus());

        System.out.println("── RANKER OUTPUT (" + response.rankedEvidenceIds().size() + " items) ──");
        int rank = 1;
        for (String id : response.rankedEvidenceIds()) {
            System.out.println("  [" + (rank++) + "] " + id);
        }
        System.out.println("Expected: " + c.expectation());
        System.out.println("──────────────────────────────────────────────────────────");

        List<String> out = response.rankedEvidenceIds();
        assertThat(out).as("ranker returned a non-empty ranking").isNotEmpty();
        assertThat(out).as("every ranked id is a real input id")
            .allMatch(id -> c.evidence().stream().anyMatch(ev -> ev.id().equals(id)));

        // Dedup identity checks: not just "the count dropped", but the *right* ids survived/left.
        if (!c.mustKeep().isEmpty()) {
            assertThat(out).as("%s: these ids must survive deduplication", c.id())
                .containsAll(c.mustKeep());
        }
        if (!c.mustRemove().isEmpty()) {
            assertThat(out).as("%s: these duplicate ids must be removed", c.id())
                .doesNotContainAnyElementsOf(c.mustRemove());
        }

        // Group-level dedup: each declared duplicate group must collapse to at most one survivor,
        // without pinning which one (the LLM may keep any member). Robust where the survivor varies.
        //
        // Deliberately <=1, not ==1: a declared pair can legitimately end with ZERO survivors when
        // (a) the top-N cap drops the survivor as low-relevance, or (b) the pair is also a duplicate
        // of a richer item outside the group, so the model merges it away entirely. Both are correct
        // dedup. "Exactly one survives" is asserted instead by the small, cap-free cases via
        // kept()/removed() (TC-DUP-003, TC-COMBO-001), where neither escape hatch applies.
        for (List<String> group : c.dupGroups()) {
            long survivors = out.stream().filter(group::contains).count();
            assertThat(survivors)
                .as("%s: at most one of duplicate group %s may survive", c.id(), group)
                .isLessThanOrEqualTo(1);
        }

        switch (c.kind()) {
            case MERGE -> assertThat(out.size())
                .as("%s: duplicates should be merged (in=%d)", c.id(), c.evidence().size())
                .isLessThan(c.evidence().size());
            case KEEP_BOTH -> assertThat(out.size())
                .as("%s: distinct facts should all be kept", c.id())
                .isEqualTo(c.evidence().size());
            case TOP2 -> assertThat(out.subList(0, Math.min(2, out.size())))
                .as("%s: both very-high items should occupy the top positions", c.id())
                .containsExactlyInAnyOrderElementsOf(ids(c.evidence()));
            case COMBO -> {
                assertThat(out.size())
                    .as("%s: duplicates removed from a 10-item set", c.id())
                    .isLessThan(c.evidence().size());
                assertThat(out.subList(0, Math.min(3, out.size())))
                    .as("%s: unrelated items (disk I/O, network) must not rank in the top 3", c.id())
                    .doesNotContain("ev8", "ev10");
            }
            case TIER_ORDER -> assertTierMonotonic(c, out);
            case SCALE -> {
                // The realistic path: a large harvest where dedup AND the top-N cap both do work.
                // The cap is enforced in CODE (computeRanking limits to MAX_RANKED_ITEMS); this asserts
                // the whole pipeline honours it end-to-end — the model dedups + scores, the code caps.
                assertThat(out.size())
                    .as("%s: ranking must be capped at %d items (top-N)", c.id(), MAX_RANKED)
                    .isLessThanOrEqualTo(MAX_RANKED);
                assertThat(out.size())
                    .as("%s: dedup + cap reduce the set below the %d inputs", c.id(), c.evidence().size())
                    .isLessThan(c.evidence().size());
                // Only the declared duplicate pairs are removable by dedup; capping the remainder at 20
                // therefore necessarily drops several low-relevance items too — proof the cap, not just
                // dedup, is doing work. (Each pair already checked by the group-level assertion above.)

                // The output is a ranked list, so verify order too — but NOT strict tier-monotonicity.
                // At production scale the model does not cleanly separate adjacent tiers: it rates
                // several cause-level HIGH items the same 1.0 as the VERY_HIGH kill, and can shuffle
                // HIGH vs MEDIUM. The stable property the cap+ranking must guarantee is coarser and is
                // what a real triage cares about: the relevant backbone outranks the irrelevant tail.
                assertBackboneOutranksTail(c, out);
            }
        }
    }

    /**
     * Asserts the ranked output is tier-monotonic: reading top to bottom, no item sits above one from
     * a higher relevance tier. Same-tier ties may appear in any order. Only ids with a declared tier
     * are considered — untagged ids (e.g. debatable duplicate survivors) are skipped — and the full
     * sequence is printed for eyeball verification.
     */
    private static void assertTierMonotonic(RankCase c, List<String> out) {
        Map<String, Tier> tierById = c.tierById();
        List<Tier> tiersInOrder = out.stream()
            .map(tierById::get)
            .filter(Objects::nonNull)
            .toList();

        System.out.println("── TIER SEQUENCE ──");
        for (int i = 0; i < out.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + out.get(i) + " → " + tierById.get(out.get(i)));
        }

        for (int i = 1; i < tiersInOrder.size(); i++) {
            assertThat(tiersInOrder.get(i).rank())
                .as("%s: item at #%d (tier %s) outranks the item above it (tier %s) — tier inversion",
                    c.id(), i + 1, tiersInOrder.get(i), tiersInOrder.get(i - 1))
                .isGreaterThanOrEqualTo(tiersInOrder.get(i - 1).rank());
        }
    }

    /**
     * Coarser order check for the realistic SCALE case: every surviving relevant item (Very High or
     * High) must rank above every surviving low-relevance tail item (Low). Unlike
     * {@link #assertTierMonotonic}, this does not require Very High vs High (or High vs Medium) to be
     * cleanly separated — at a 30-item scale the model ties those tiers, so only the relevant-vs-tail
     * boundary is a stable property. The full sequence is printed for eyeball verification.
     */
    private static void assertBackboneOutranksTail(RankCase c, List<String> out) {
        Map<String, Tier> tierById = c.tierById();

        System.out.println("── TIER SEQUENCE ──");
        for (int i = 0; i < out.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + out.get(i) + " → " + tierById.get(out.get(i)));
        }

        int lastBackbone = -1;              // lowest rank held by a Very High / High item
        int firstTail = Integer.MAX_VALUE;  // highest rank held by a Low item
        for (int i = 0; i < out.size(); i++) {
            Tier t = tierById.get(out.get(i));
            if (t == Tier.VERY_HIGH || t == Tier.HIGH) lastBackbone = i;
            else if (t == Tier.LOW && firstTail == Integer.MAX_VALUE) firstTail = i;
        }

        if (lastBackbone >= 0 && firstTail != Integer.MAX_VALUE) {
            assertThat(lastBackbone)
                .as("%s: every relevant (Very High/High) item must rank above every low-relevance "
                    + "tail item — backbone sank below the tail", c.id())
                .isLessThan(firstTail);
        }
    }

    // -------------------------------------------------------------------------
    // Test data — mirrors docs/testing/relevance-dedup-test-cases.md
    // -------------------------------------------------------------------------

    static List<RankCase> cases() {
        List<RankCase> cs = new ArrayList<>();

        // ---- Part 1: Deduplication ----
        cs.add(new RankCase("TC-DUP-001", "Same metric, same value", Kind.MERGE,
            rca("Memory limit misconfiguration", "Container memory limit set too low at 256Mi"),
            "SUPPORTED", 0.85,
            ev("ev1", "Memory limit 256Mi"),
            ev("ev2", "Pod capped at 256 MiB"))
            .expect("MERGE → 2 in, 1 out"));

        cs.add(new RankCase("TC-DUP-002", "Paraphrased statement", Kind.MERGE,
            rca("CPU request misconfiguration", "CPU request set to 100m"),
            "SUPPORTED", 0.85,
            ev("ev1", "CPU request 100m"),
            ev("ev2", "CPU request 100 millicores"))
            .expect("MERGE → 2 in, 1 out"));

        cs.add(new RankCase("TC-DUP-003", "Same fact, different precision", Kind.MERGE,
            rca("GC pause", "Serial GC pause of ~53ms"),
            "SUPPORTED", 0.80,
            ev("ev1", "GC pause: 53.225ms"),
            ev("ev2", "GC pause: 53ms"),
            ev("ev3", "GC pause: ~53 milliseconds"))
            .expect("MERGE → 3 in, 1 out (most precise kept)")
            .kept("ev1")              // 53.225ms is the most precise statement
            .removed("ev2", "ev3"));

        cs.add(new RankCase("TC-DUP-004", "Cause vs symptom (keep both)", Kind.KEEP_BOTH,
            rca("Heap pressure", "Heap exhaustion driving Full GC"),
            "SUPPORTED", 0.85,
            ev("ev1", "Heap usage 98%"),
            ev("ev2", "15 Full GC events in last 30 seconds"))
            .expect("KEEP BOTH → 2 in, 2 out"));

        cs.add(new RankCase("TC-DUP-005", "Different metrics (keep both)", Kind.KEEP_BOTH,
            rca("Memory sizing", "Memory usage approaching limit"),
            "SUPPORTED", 0.85,
            ev("ev1", "Memory limit 256Mi"),
            ev("ev2", "Memory usage 250Mi"))
            .expect("KEEP BOTH → 2 in, 2 out"));

        cs.add(new RankCase("TC-DUP-006", "Different aspects of same component", Kind.KEEP_BOTH,
            rca("GC configuration", "Serial GC causing long pauses"),
            "SUPPORTED", 0.80,
            ev("ev1", "Serial GC algorithm detected"),
            ev("ev2", "Serial GC pause time: 53ms"))
            .expect("KEEP BOTH → 2 in, 2 out"));

        // Same metric + same value but DIFFERENT components — the one case most at risk of a wrong
        // merge. Identical wording except the pod name must still be kept as two independent facts.
        cs.add(new RankCase("TC-DUP-007", "Same metric, different pods (keep both)", Kind.KEEP_BOTH,
            rca("Pod-a heap OOM", "Heap exhaustion OOMKilled pod-a"),
            "SUPPORTED", 0.85,
            ev("ev1", "Pod pod-a heap usage at 98% of max"),
            ev("ev2", "Pod pod-b heap usage at 98% of max"))
            .expect("KEEP BOTH → different components are not duplicates"));

        // ---- Part 2: Relevance scoring ----
        cs.add(new RankCase("TC-REL-001", "Directly supports RCA (Very High)", Kind.TOP2,
            rca("Heap too small", "Heap size configured too small (378M)"),
            "SUPPORTED", 0.85,
            ev("ev1", "Heap explicitly set to -Xmx378m"),
            ev("ev2", "Heap reached maximum capacity at GC(5)"))
            .expect("both in top 2"));

        cs.add(new RankCase("TC-REL-002", "IS the root cause (Very High)", Kind.TOP2,
            rca("Heap exhaustion OOM", "OutOfMemory error caused by heap exhaustion"),
            "SUPPORTED", 0.90,
            ev("ev1", "java.lang.OutOfMemoryError: Java heap space"),
            ev("ev2", "Container killed with OOMKilled status"))
            .expect("both in top 2"));

        cs.add(new RankCase("TC-REL-003", "Refuting evidence special rule (UNSUPPORTED, low conf)", Kind.TOP2,
            rca("Memory leak OOM", "Memory leak causing OOM"),
            "UNSUPPORTED", 0.25,
            ev("ev1", "Memory usage stable at 60-65% for 24 hours"),
            ev("ev2", "No upward trend in heap usage across 1000 GC cycles"))
            .expect("refuting evidence → top 2 (UNSUPPORTED + <50%)"));

        cs.add(new RankCase("TC-REL-004", "Shows cause not symptom (High)", Kind.KEEP_BOTH,
            rca("GC latency", "GC pauses causing latency spikes"),
            "SUPPORTED", 0.80,
            ev("ev1", "Heap at 98% capacity triggering continuous Full GC"),
            ev("ev2", "Serial GC algorithm causing stop-the-world pauses"))
            .expect("ranked below Very High, above Medium"));

        cs.add(new RankCase("TC-REL-005", "Critical context (High)", Kind.KEEP_BOTH,
            rca("Heap vs container sizing", "Heap sized at 378M insufficient for 650Mi container"),
            "SUPPORTED", 0.80,
            ev("ev1", "Container memory limit: 650 MiB"),
            ev("ev2", "JVM heap configured: -Xmx378m (58% of container)"))
            .expect("ranked High (explains WHY)"));

        cs.add(new RankCase("TC-REL-006", "Symptom rather than cause (Medium)", Kind.KEEP_BOTH,
            rca("Allocation failures", "Heap size too small causing allocation failures"),
            "SUPPORTED", 0.80,
            ev("ev1", "Application latency p99 increased to 500ms"),
            ev("ev2", "Request timeout rate: 15%"))
            .expect("ranked below High, above Low"));

        cs.add(new RankCase("TC-REL-007", "Background context (Medium)", Kind.KEEP_BOTH,
            rca("GC SLO breach", "GC pause times exceeding SLO"),
            "SUPPORTED", 0.80,
            ev("ev1", "Application scenario: PROMOTION_PRESSURE workload"),
            ev("ev2", "JVM version: OpenJDK 17.0.8"))
            .expect("ranked Medium / indirect"));

        cs.add(new RankCase("TC-REL-008", "Different component (Low)", Kind.KEEP_BOTH,
            rca("Heap OOM", "Heap size too small causing OOM"),
            "SUPPORTED", 0.80,
            ev("ev1", "Disk I/O: 500 IOPS"),
            ev("ev2", "Network latency: 2ms p95"))
            .expect("ranked at bottom or filtered"));

        // ---- Part 3: Integration ----
        cs.add(new RankCase("TC-COMBO-001", "Dedup + relevance (10 items)", Kind.COMBO,
            rca("Heap too small", "Heap size too small"),
            "SUPPORTED", 0.85,
            ev("ev1", "Heap -Xmx378m"),              // Very High
            ev("ev2", "Maximum heap: 378MB"),         // DUPLICATE of ev1
            ev("ev3", "Heap reached max capacity"),   // Very High
            ev("ev4", "Heap at 98%"),                 // High (cause)
            ev("ev5", "15 Full GC events"),           // High (symptom of heap pressure)
            ev("ev6", "Application latency 500ms"),    // Medium (symptom)
            ev("ev7", "Container limit 650Mi"),        // Medium (context)
            ev("ev8", "Disk I/O 500 IOPS"),            // Low (unrelated)
            ev("ev9", "Heap 378 MiB"),                 // DUPLICATE of ev1
            ev("ev10", "Network latency 2ms"))         // Low (unrelated)
            .expect("10 → 7-8 (remove ev2, ev9); top 2 = ev1/ev3; bottom = ev8/ev10")
            .kept("ev1")                 // the surviving member of the ev1/ev2/ev9 duplicate group
            .removed("ev2", "ev9")       // duplicates of ev1 must be dropped
            .shuffled());                // don't let declaration order feed the top-3 check

        // ---- Part 4: Ranking order (cross-tier) ----
        // The only cases that actually validate ranking order: a pool spanning all four relevance
        // tiers, so a correct ranker must place them Very High → High → Medium → Low.

        // One item per tier — tier-monotonic here means an exact total order: vh, high, med, low.
        // HIGH is a GC-pressure signal, NOT a near-exhaustion statement: the model buckets relevance
        // into 4 tiers and rates terminal exhaustion ("heap at 98%/100%") the same 1.0 as the OOM error
        // itself, so an "almost out of heap" item tagged HIGH would legitimately tie VERY_HIGH and the
        // order check would flip on tie-break. "15 Full GC events" is reliably rated 0.75 (High).
        cs.add(tiered("TC-RANK-001", "Four tiers, one item each",
            rca("Heap exhaustion OOM", "Heap exhaustion caused an OutOfMemory kill"),
            "SUPPORTED", 0.90,
            new TieredEv("vh",   "java.lang.OutOfMemoryError: Java heap space", Tier.VERY_HIGH),
            new TieredEv("high", "15 Full GC events in the 30 seconds before the kill", Tier.HIGH),
            new TieredEv("med",  "Request latency p99 rose to 500ms", Tier.MEDIUM),
            new TieredEv("low",  "Node disk I/O at 500 IOPS", Tier.LOW)));

        // Two items per tier — validates tier ordering with same-tier ties allowed. Tiers are kept
        // unambiguous on purpose; the debatable "config vs symptom" boundary is covered by COMBO-001.
        // Same calibration rule as TC-RANK-001: BOTH HIGH items are GC-pressure signals, never a
        // near-exhaustion statement. "Heap reached 100%" proves the OOM mechanism, so the model rates it
        // the same 1.0 as the OOM error (VERY_HIGH) and it would legitimately tie — and tie-break flip —
        // the VERY_HIGH items. Full-GC count and Full-GC pause time are distinct facts (RELATED, both
        // kept by dedup) that the model reliably rates 0.75 (High).
        cs.add(tiered("TC-RANK-002", "Four tiers, two items each",
            rca("Heap exhaustion OOM", "Heap exhaustion caused an OutOfMemory kill (OOMKilled)"),
            "SUPPORTED", 0.88,
            new TieredEv("vh1",  "java.lang.OutOfMemoryError: Java heap space", Tier.VERY_HIGH),
            new TieredEv("vh2",  "Container terminated with reason: OOMKilled", Tier.VERY_HIGH),
            new TieredEv("hi1",  "Average Full GC pause time rose to 2.5s before the kill", Tier.HIGH),
            new TieredEv("hi2",  "15 Full GC events in the 30s before the kill", Tier.HIGH),
            new TieredEv("md1",  "Application p99 latency rose to 500ms", Tier.MEDIUM),
            new TieredEv("md2",  "Request timeout rate climbed to 15%", Tier.MEDIUM),
            new TieredEv("lo1",  "Node disk I/O at 500 IOPS", Tier.LOW),
            new TieredEv("lo2",  "Network latency 2ms p95", Tier.LOW)));

        // CONTRADICTED status — the refuting-evidence special rule. For a memory-leak RCA the engine
        // disproved, the two items that refute the leak must outrank the OOMKilled outcome (which
        // confirms the termination but not the claimed leak mechanism). Shuffled by tiered().
        cs.add(tiered("TC-REL-010", "Contradicted RCA — refuting evidence ranks highest",
            rca("Memory leak OOM", "A memory leak caused the OutOfMemory kill"),
            "CONTRADICTED", 0.20,
            new TieredEv("ev1", "No upward trend in heap usage across 1000 GC cycles", Tier.VERY_HIGH),
            new TieredEv("ev2", "Heap usage stable at 60-65% for 24 hours", Tier.VERY_HIGH),
            new TieredEv("ev3", "Container was OOMKilled (exit code 137)", Tier.HIGH)));

        // ---- Part 6: Strength participates in the score ----
        // The 0.2×strength term of FinalScore = 0.2×strength + 0.8×relevance is applied in CODE, not by
        // the model — so it is proven deterministically in LlmEvidenceRankerScoringTest rather than here
        // (the LLM assigns relevance only, and does not reliably apply the arithmetic, which is exactly
        // why scoring was moved into code). This IT focuses on what needs the real model: dedup and
        // relevance assignment, with the code-side scoring exercised end-to-end by the cases below.

        // ---- Part 5: Realistic scale ----
        // A production-sized harvest: ~30 raw evidence items, with five duplicate pairs (→ 25 unique
        // after dedup) and a long tail of clearly-unrelated low-relevance items. Capping the ranked
        // output at MAX_RANKED_ITEMS=20 forces the ranker to both merge duplicates AND drop the
        // weakest items — the path a real OOM diagnosis takes, which no small focused case exercises.
        cs.add(new RankCase("TC-SCALE-001", "~30-item OOM harvest → dedup → top-20 cap", Kind.SCALE,
            rca("Heap exhaustion OOM", "JVM heap exhaustion (-Xmx512m) caused the container to be OOMKilled"),
            "SUPPORTED", 0.88,
            // Very High — the kill itself and its direct mechanism (must survive)
            ev("vh1", "java.lang.OutOfMemoryError: Java heap space in application logs"),
            ev("vh2", "Container terminated with reason OOMKilled, exit code 137"),
            ev("vh3", "Heap reached 100% of -Xmx512m at GC(7) immediately before termination"),
            // High — cause / mechanism (should survive)
            ev("hi1", "15 Full GC events in the 30 seconds before the kill"),
            ev("hi2", "Heap usage climbed from 70% to 98% over the preceding 10 minutes"),
            ev("hi3", "-Xmx512m is 80% of the 650Mi container memory limit, leaving little headroom"),
            ev("hi4", "GC overhead consumed 45% of CPU time in the final minute"),
            // Medium — symptoms / context (likely survive, ranked lower)
            ev("md1", "Application p99 latency rose to 500ms before the crash"),
            ev("md2", "Request timeout rate climbed to 15%"),
            ev("md3", "Pod restart count increased to 4 over the past hour"),
            ev("md4", "Readiness probe failures coincided with the long GC pauses"),
            // Five duplicate pairs — each must collapse to a single surviving id
            ev("p1a", "Memory limit 512Mi"),
            ev("p1b", "Container capped at 512 MiB"),
            ev("p2a", "Exit code 137"),
            ev("p2b", "Last state OOMKilled, exit code 137"),
            ev("p3a", "Full GC pause of 53ms observed"),
            ev("p3b", "Full GC pause time: ~53 milliseconds"),
            ev("p4a", "Heap at 98% of max"),
            ev("p4b", "Heap usage reached 98% of the configured maximum"),
            ev("p5a", "JVM flag -Xmx512m set"),
            ev("p5b", "Max heap configured to 512m via -Xmx"),
            // Long tail — clearly unrelated low-relevance items; the cap must drop several of these
            ev("lo1", "Node disk I/O at 500 IOPS"),
            ev("lo2", "Network latency 2ms p95 between pods"),
            ev("lo3", "Node CPU averaged 40% across the window"),
            ev("lo4", "DNS resolution averaged 5ms"),
            ev("lo5", "A sibling pod in the namespace reported healthy"),
            ev("lo6", "Ingress request rate steady at 200 rps"),
            ev("lo7", "ConfigMap app-config last updated 3 days ago"),
            ev("lo8", "Node kernel version 5.14 unchanged"),
            ev("lo9", "Cluster autoscaler made no scaling decisions in the window"))
            .expect("30 in → 25 after dedup → 20 out (cap drops the weakest low-relevance items)")
            .kept("vh1", "vh2", "vh3")        // the OOM kill + mechanism are never dropped
            .dupGroup("p1a", "p1b")
            .dupGroup("p2a", "p2b")
            .dupGroup("p3a", "p3b")
            .dupGroup("p4a", "p4b")
            .dupGroup("p5a", "p5b")
            // Order check: tier only the unambiguous backbone (the p* duplicate survivors have a
            // debatable tier and an unknown survivor, so they're left out of the ordering assertion).
            .tiers(
                new TieredEv("vh1", "", Tier.VERY_HIGH),
                new TieredEv("vh2", "", Tier.VERY_HIGH),
                new TieredEv("vh3", "", Tier.VERY_HIGH),
                new TieredEv("hi1", "", Tier.HIGH),
                new TieredEv("hi2", "", Tier.HIGH),
                new TieredEv("hi3", "", Tier.HIGH),
                new TieredEv("hi4", "", Tier.HIGH),
                new TieredEv("md1", "", Tier.MEDIUM),
                new TieredEv("md2", "", Tier.MEDIUM),
                new TieredEv("md3", "", Tier.MEDIUM),
                new TieredEv("md4", "", Tier.MEDIUM),
                new TieredEv("lo1", "", Tier.LOW),
                new TieredEv("lo2", "", Tier.LOW),
                new TieredEv("lo3", "", Tier.LOW),
                new TieredEv("lo4", "", Tier.LOW),
                new TieredEv("lo5", "", Tier.LOW),
                new TieredEv("lo6", "", Tier.LOW),
                new TieredEv("lo7", "", Tier.LOW),
                new TieredEv("lo8", "", Tier.LOW),
                new TieredEv("lo9", "", Tier.LOW))
            // Declaration order above is tier-sorted for readability; shuffle so the order check is real.
            .shuffled());

        return cs;
    }

    // -------------------------------------------------------------------------
    // Helpers + test-data model
    // -------------------------------------------------------------------------

    private static RcaSummary rca(String issueSummary, String rootCause) {
        return new RcaSummary(issueSummary, rootCause);
    }

    /**
     * Narrated evidence with a stable id and a statement; strength is pinned to {@link #FIXED_STRENGTH}
     * so ordering here is driven purely by LLM-assigned relevance. Strength-sensitivity of the score is
     * covered separately by {@link com.causa.core.services.validation.impl.LlmEvidenceRankerScoringTest}.
     */
    private static ValidationEvidence ev(String id, String statement) {
        return ValidationEvidence.of(id, "test", EvidenceType.OTHER, statement, statement, "", FIXED_STRENGTH);
    }

    private static List<String> ids(List<ValidationEvidence> evidence) {
        return evidence.stream().map(ValidationEvidence::id).toList();
    }

    enum Kind { MERGE, KEEP_BOTH, TOP2, COMBO, TIER_ORDER, SCALE }

    /**
     * RCA-relevance tiers from the prompt (Very High → Low). {@link #rank()} is the sort priority:
     * lower value = ranked higher. A correct ranking is tier-monotonic — no item ever sits above
     * one from a higher tier.
     */
    enum Tier {
        VERY_HIGH, HIGH, MEDIUM, LOW;
        int rank() { return ordinal(); }
    }

    /** An evidence item tagged with the tier the ranker should assign it for the case's RCA. */
    record TieredEv(String id, String statement, Tier tier) {}

    record RankCase(
        String id,
        String title,
        Kind kind,
        RcaSummary rca,
        String validationStatus,
        double validationScore,
        List<ValidationEvidence> evidence,
        List<TieredEv> tiered,
        List<String> mustKeep,
        List<String> mustRemove,
        List<List<String>> dupGroups,
        String expectation
    ) {
        RankCase(String id, String title, Kind kind, RcaSummary rca,
                 String validationStatus, double validationScore, ValidationEvidence... evidence) {
            this(id, title, kind, rca, validationStatus, validationScore,
                List.of(evidence), List.of(), List.of(), List.of(), List.of(), "");
        }

        RankCase expect(String expectation) {
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                evidence, tiered, mustKeep, mustRemove, dupGroups, expectation);
        }

        /** Ids that must survive deduplication (e.g. the most-precise item of a duplicate group). */
        RankCase kept(String... ids) {
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                evidence, tiered, List.of(ids), mustRemove, dupGroups, expectation);
        }

        /** Ids that must be removed as duplicates. */
        RankCase removed(String... ids) {
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                evidence, tiered, mustKeep, List.of(ids), dupGroups, expectation);
        }

        /**
         * Attaches relevance tiers to (a subset of) the evidence, enabling the tier-monotonic order
         * check. Only id and tier are read; untagged ids are skipped by the order check — use this to
         * tier the unambiguous backbone of a noisy case while leaving debatable items out of ordering.
         */
        RankCase tiers(TieredEv... items) {
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                evidence, List.of(items), mustKeep, mustRemove, dupGroups, expectation);
        }

        /**
         * Shuffles the evidence (fixed seed) before it reaches the ranker, so a ranker that merely
         * echoed its input order cannot pass an ordering or top-N assertion. Use on any case whose
         * declaration order would otherwise leak the expected ranking.
         */
        RankCase shuffled() {
            List<ValidationEvidence> shuffled = new ArrayList<>(evidence);
            Collections.shuffle(shuffled, new Random(SHUFFLE_SEED));
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                List.copyOf(shuffled), tiered, mustKeep, mustRemove, dupGroups, expectation);
        }

        /**
         * Declares a set of ids that are duplicates of each other. At most one may survive, but which
         * one is the LLM's call — so this asserts the group collapses without pinning the survivor.
         * Chainable, so a case can declare several independent duplicate groups.
         */
        RankCase dupGroup(String... ids) {
            List<List<String>> groups = new ArrayList<>(dupGroups);
            groups.add(List.of(ids));
            return new RankCase(id, title, kind, rca, validationStatus, validationScore,
                evidence, tiered, mustKeep, mustRemove, List.copyOf(groups), expectation);
        }

        /** Tier lookup for ranking validation (empty for non-tiered cases). */
        Map<String, Tier> tierById() {
            return tiered.stream().collect(Collectors.toMap(TieredEv::id, TieredEv::tier));
        }

        @Override
        public String toString() {
            return id + " (" + title + ")";
        }
    }

    /** Fixed seed so the shuffled input order is deterministic across runs. */
    private static final long SHUFFLE_SEED = 20251103L;

    /**
     * Builds a multi-tier ranking case: each item carries the tier the ranker should assign, and the
     * evidence list is derived from them (strength fixed, so ordering is driven purely by relevance).
     *
     * <p>The input is <b>shuffled</b> (fixed seed) before it reaches the ranker. Items are declared
     * Very High → Low for readability, so without the shuffle a ranker that merely echoed its input
     * would pass; shuffling forces it to actually re-order by relevance.
     */
    private static RankCase tiered(String id, String title, RcaSummary rca,
                                   String validationStatus, double validationScore, TieredEv... items) {
        List<ValidationEvidence> evidence = Arrays.stream(items)
            .map(t -> ev(t.id(), t.statement()))
            .collect(Collectors.toList());
        Collections.shuffle(evidence, new Random(SHUFFLE_SEED));
        return new RankCase(id, title, Kind.TIER_ORDER, rca, validationStatus, validationScore,
            evidence, List.of(items), List.of(), List.of(), List.of(),
            "tier-monotonic ordering (Very High → Low)");
    }
}
