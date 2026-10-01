package com.causa.core.services.evidence;

import com.causa.common.constants.EvidenceConstants.Sources;
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
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
 * <p>The table is derived from {@code mcp.json}, which already states the mapping: a server's
 * {@code tools[].contextKey} is the section header rendered for that tool's output, under the
 * server name it is declared beneath. A new MCP server therefore needs no change here.
 *
 * <p>Labels are matched exact-first, then by longest prefix, then by containment, which absorbs
 * the decorations the LLM adds in practice:
 *
 * <pre>
 *   "POD_LOGS - sequence 124"                  → kubernetes
 *   "POD_LOGS — sequence 125"                  → kubernetes  (em-dash)
 *   "POD_LOGS and POD_LOGS_PREVIOUS"           → kubernetes
 *   "GC_ANALYSIS: No Data Available"           → cryostat
 * </pre>
 *
 * <p>An unrecognised label resolves to {@link Sources#UNKNOWN} rather than throwing; the
 * original string is retained in evidence metadata so gaps surface in the data instead of
 * failing the pipeline.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpSourceResolver {

    private final McpRegistry registry;

    /**
     * Section label → canonical MCP server. Built on first use, not at construction: CDI may
     * instantiate this bean before {@link McpRegistry#init} has run. A benign race just rebuilds
     * it — the table is a pure function of the registry.
     */
    private volatile Map<String, String> sectionToSource;

    @Inject
    public McpSourceResolver(McpRegistry registry) {
        this.registry = registry;
    }

    /**
     * Resolves a source label to a canonical MCP server name.
     *
     * @param sourceLabel the section label, as emitted by the LLM or stamped by the signal
     *                    extractor; may be null or blank
     * @return the canonical MCP server name, or {@link Sources#UNKNOWN} if unrecognised
     */
    public String resolve(String sourceLabel) {
        String normalized = normalize(sourceLabel);
        if (normalized.isEmpty()) {
            return Sources.UNKNOWN;
        }

        Map<String, String> table = table();

        String exact = table.get(normalized);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return Sources.UNKNOWN;
    }

    private Map<String, String> table() {
        Map<String, String> table = sectionToSource;
        if (table == null) {
            table = buildSectionMap(registry.allClients());
            // An empty table means the registry has not been init'd (or failed to) — don't cache
            // that, or an early call would pin every later resolution to unknown.
            if (!table.isEmpty()) {
                sectionToSource = table;
            }
        }
        return table;
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

    /** Declared {@code contextKey}s → server name, longest label first so the most specific wins. */
    static Map<String, String> buildSectionMap(Collection<McpClient> clients) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (McpClient client : clients) {
            for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
                if (tool.contextKey() != null && !tool.contextKey().isBlank()) {
                    raw.put(tool.contextKey(), client.getServerName());
                }
            }
        }

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
