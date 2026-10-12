package com.causa.core.services.evidence;

import com.causa.common.constants.EvidenceConstants.Sources;
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * MCP Source Resolver
 *
 * <p>Maps a diagnostic context section label onto the MCP server that produced it.
 *
 * <p>Evidence reaches us labelled with a section name rather than a server name — PATH A
 * evidence carries whatever string the LLM echoed from the context it was shown, and PATH B
 * signals are stamped with the section they were extracted from. Both need normalising to a
 * single server identifier so evidence can be attributed to the source that actually
 * produced it.
 *
 * <p>The table is read from {@code mcp.json} via {@link McpRegistry}, not hardcoded: every
 * section the LLM ever sees is rendered from a tool's {@code contextKey} under its server
 * entry, so the config already states which server owns which label. A server added to
 * {@code mcp.json} is resolvable with no code change, and a server absent from the deployment
 * never attributes evidence to itself.
 *
 * <p>Labels are matched exact-first, then by prefix in either direction, then by containment,
 * which absorbs the decorations the LLM adds and the shortenings collectors apply:
 *
 * <pre>
 *   "POD_LOGS - sequence 124"      → kubernetes  (label decorated)
 *   "LIBERTY_LOGS"                 → filesystem  (LIBERTY_LOGS_DIRECTORY_LISTING shortened)
 *   "GC ANALYSIS (Cryostat JFR)"   → unknown     (not a contextKey any configured server emits)
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

    @Inject
    public McpSourceResolver(McpRegistry registry) {
        this.registry = registry;
    }

    /**
     * Resolves a source label to the MCP server name that produced it.
     *
     * @param sourceLabel the section label, as emitted by the LLM or stamped by the signal
     *                    extractor; may be null or blank
     * @return the MCP server name as declared in {@code mcp.json}, or {@link Sources#UNKNOWN}
     *         if unrecognised
     */
    public String resolve(String sourceLabel) {
        String normalized = normalize(sourceLabel);
        if (normalized.isEmpty()) {
            return Sources.UNKNOWN;
        }

        // Longest contextKey first, so the most specific section wins a prefix match.
        List<Map.Entry<String, String>> table = contextKeyToServer();

        for (Map.Entry<String, String> entry : table) {
            if (normalized.equals(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : table) {
            // Second direction covers a collector that puts a shortened key (LIBERTY_LOGS for
            // LIBERTY_LOGS_DIRECTORY_LISTING); the "_" boundary keeps it from matching a
            // fragment that merely happens to start the same way.
            if (normalized.startsWith(entry.getKey()) || entry.getKey().startsWith(normalized + "_")) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : table) {
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
     * Every {@code contextKey} declared in {@code mcp.json}, paired with its server and sorted
     * longest-key-first. Rebuilt per call — the registry holds a handful of servers with a
     * handful of tools each, and reading it live means a registry re-init is picked up with no
     * invalidation logic.
     */
    private List<Map.Entry<String, String>> contextKeyToServer() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (McpClient client : registry.allClients()) {
            for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
                String key = normalize(tool.contextKey());
                if (!key.isEmpty()) {
                    entries.add(Map.entry(key, client.getServerName()));
                }
            }
        }
        entries.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        return entries;
    }
}
