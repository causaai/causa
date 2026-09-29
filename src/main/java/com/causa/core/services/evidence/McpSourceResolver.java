package com.causa.core.services.evidence;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.EvidenceConstants.Sources;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Source Resolver
 *
 * <p>Maps a diagnostic context section label onto the canonical MCP server that produced it.
 *
 * <p>Evidence reaches us labelled with a section name rather than a server name — PATH A
 * evidence carries whatever string the LLM echoed from the context it was shown, and PATH B
 * signals are stamped with the section they were extracted from. Both need normalising to a
 * single server identifier so evidence can be attributed to the source that actually
 * produced it.
 *
 * <p>Matching is deterministic and needs no LLM call, because the section headers in
 * {@link ContextConstants} already name their server — {@code (Cryostat JFR)},
 * {@code (Kruize)}, {@code (JMX MCP)} — and the Kubernetes sections are the unsuffixed
 * remainder. Labels are matched exact-first, then by longest prefix, then by containment,
 * which absorbs the decorations the LLM adds in practice:
 *
 * <pre>
 *   "POD LOGS (recent) - sequence 124"                            → kubernetes
 *   "POD LOGS (recent) — sequence 125"                            → kubernetes  (em-dash)
 *   "POD LOGS (recent) and PREVIOUS CONTAINER LOGS (pre-crash)"   → kubernetes
 *   "GC ANALYSIS (Cryostat JFR): No Data Available"               → cryostat
 * </pre>
 *
 * <p>An unrecognised label resolves to {@link Sources#UNKNOWN} rather than throwing; the
 * original string is retained in evidence metadata so gaps in this table surface in the data
 * instead of failing the pipeline.
 *
 * @since 0.0.1
 */
public final class McpSourceResolver {

    /**
     * Section label → canonical MCP server, ordered longest-label-first so that prefix
     * matching always picks the most specific section.
     */
    private static final Map<String, String> SECTION_TO_SOURCE = buildSectionMap();

    private McpSourceResolver() {
        // Prevent instantiation
    }

    /**
     * Resolves a source label to a canonical MCP server name.
     *
     * @param sourceLabel the section label, as emitted by the LLM or stamped by the signal
     *                    extractor; may be null or blank
     * @return the canonical MCP server name, or {@link Sources#UNKNOWN} if unrecognised
     */
    public static String resolve(String sourceLabel) {
        String normalized = normalize(sourceLabel);
        if (normalized.isEmpty()) {
            return Sources.UNKNOWN;
        }

        String exact = SECTION_TO_SOURCE.get(normalized);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, String> entry : SECTION_TO_SOURCE.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : SECTION_TO_SOURCE.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return Sources.UNKNOWN;
    }

    /**
     * Normalises a label for comparison: uppercased, whitespace collapsed, trimmed.
     *
     * <p>Uppercasing makes matching case-insensitive; collapsing whitespace absorbs the
     * line wrapping and double spaces that appear in LLM-echoed labels.
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    /**
     * Builds the section → server table from {@link ContextConstants}, sorted by descending
     * label length so the longest (most specific) section wins a prefix match.
     */
    private static Map<String, String> buildSectionMap() {
        Map<String, String> raw = new LinkedHashMap<>();

        // Kubernetes MCP — pods_get, events_list, pods_log
        raw.put(ContextConstants.SECTION_POD_STATUS, Sources.KUBERNETES);
        raw.put(ContextConstants.SECTION_POD_EVENTS, Sources.KUBERNETES);
        raw.put(ContextConstants.SECTION_POD_LOGS, Sources.KUBERNETES);
        raw.put(ContextConstants.SECTION_PREVIOUS_POD_LOGS, Sources.KUBERNETES);

        // Kruize MCP — resource recommendations
        raw.put(ContextConstants.SECTION_COST_RECOMMENDATIONS, Sources.KRUIZE);
        raw.put(ContextConstants.SECTION_PERF_RECOMMENDATIONS, Sources.KRUIZE);

        // Cryostat MCP — JFR analyses
        raw.put(ContextConstants.SECTION_GC_ANALYSIS, Sources.CRYOSTAT);
        raw.put(ContextConstants.SECTION_MEMORY_ANALYSIS, Sources.CRYOSTAT);
        raw.put(ContextConstants.SECTION_THREAD_ANALYSIS, Sources.CRYOSTAT);
        raw.put(ContextConstants.SECTION_EXCEPTION_ANALYSIS, Sources.CRYOSTAT);
        raw.put(ContextConstants.SECTION_CONTAINER_ANALYSIS, Sources.CRYOSTAT);

        // Quarkus MCP — raw metrics
        raw.put(ContextConstants.SECTION_QUARKUS_RAW_METRICS, Sources.QUARKUS);

        // Async Profiler MCP
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_POD_LIST, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_JVM_STATUS, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_JVM_STATS, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_RECORDING, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_REPORT, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_JFR_SUMMARY, Sources.ASYNC_PROFILER);
        raw.put(ContextConstants.SECTION_ASYNC_PROFILER_FLAME_GRAPH, Sources.ASYNC_PROFILER);

        // Filesystem MCP — log files on VM / Liberty
        raw.put(ContextConstants.SECTION_LIBERTY_LOGS, Sources.FILESYSTEM);
        raw.put(ContextConstants.SECTION_VM_LOG_DIR_LISTING, Sources.FILESYSTEM);
        raw.put(ContextConstants.SECTION_VM_GC_LOG_CONTENT, Sources.FILESYSTEM);

        // JMX MCP — live JVM state on VM
        raw.put(ContextConstants.SECTION_VM_HEAP_STATUS, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_GC_ACTIVITY, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_THREAD_STATE, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_GC_PRESSURE, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_MEMORY_LEAK, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_THREAD_CONTENTION, Sources.JMX);
        raw.put(ContextConstants.SECTION_VM_JVM_RUNTIME_INFO, Sources.JMX);

        List<Map.Entry<String, String>> sorted = raw.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
            .toList();

        // LinkedHashMap, not Map.copyOf — iteration order *is* the longest-prefix-wins rule.
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sorted) {
            ordered.put(normalize(entry.getKey()), entry.getValue());
        }
        return Collections.unmodifiableMap(ordered);
    }
}
