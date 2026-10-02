package com.causa.core.domain;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.EvidenceConstants.Snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An index over the flattened text {@link DiagnosticContext#toString()} produces.
 *
 * <p>Signal extraction reduces that text to a normalised name/value pair and does not remember
 * where it came from. This maps a match position back to the section it fell in and the source
 * text around it, so rule-derived evidence can be read in situ instead of taken on trust.
 *
 * <p>PATH A needs none of this: the LLM returns the snippet it quoted, so that evidence
 * carries its own text.
 *
 * <p>Immutable and cheap to build: one regex pass over the section headers. Build it once per
 * diagnostic and share it.
 *
 * @since 0.0.1
 */
public final class DiagnosticContextIndex {

    /** Section header line, e.g. {@code --- POD STATUS ---} — delimits the context by source. */
    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile(
        "^" + Pattern.quote(ContextConstants.SECTION_PREFIX)
            + "(.+?)" + Pattern.quote(ContextConstants.SECTION_SUFFIX) + "$",
        Pattern.MULTILINE
    );

    private record Span(String label, int start, int end) {}

    private final String context;
    private final List<Span> spans;

    private DiagnosticContextIndex(String context, List<Span> spans) {
        this.context = context;
        this.spans = spans;
    }

    /**
     * Indexes the sections of a rendered diagnostic context.
     *
     * @param context the flattened context; may be null or blank
     * @return the index — an empty one, whose lookups all return null, when there is no context
     */
    public static DiagnosticContextIndex of(String context) {
        if (context == null || context.isBlank()) {
            return new DiagnosticContextIndex("", List.of());
        }

        List<Span> spans = new ArrayList<>();
        Matcher matcher = SECTION_HEADER_PATTERN.matcher(context);

        String label = null;
        int bodyStart = -1;
        while (matcher.find()) {
            if (label != null) {
                spans.add(new Span(label, bodyStart, matcher.start()));
            }
            label = matcher.group(1).trim();
            bodyStart = matcher.end();
        }
        if (label != null) {
            spans.add(new Span(label, bodyStart, context.length()));
        }
        return new DiagnosticContextIndex(context, spans);
    }

    /**
     * The label of the section containing {@code offset}.
     *
     * <p>Null for an offset of -1, or one landing in the context preamble — the text is still
     * valid, it simply cannot be attributed to a source.
     */
    public String labelAt(int offset) {
        Span span = spanAt(offset);
        return span != null ? span.label() : null;
    }

    /**
     * The whole context line containing {@code offset}, trimmed.
     *
     * <p>A line rather than the match itself: {@code "137"} on its own proves nothing, while
     * {@code "Exit Code: 137"} is the text the source actually emitted. Null when there is no
     * offset to read from, or the line is blank.
     */
    public String lineAt(int offset) {
        if (offset < 0 || offset >= context.length()) {
            return null;
        }
        int start = context.lastIndexOf('\n', offset) + 1;
        int end = context.indexOf('\n', offset);
        String line = (end < 0 ? context.substring(start) : context.substring(start, end)).trim();
        return line.isEmpty() ? null : line;
    }

    /**
     * The line containing {@code offset} plus {@link Snippet#CONTEXT_LINES} either side.
     *
     * <p>Clamped to the containing section: a window that ran past a {@code --- LABEL ---}
     * header would attach one server's output to another's evidence, which is worse than a
     * short window. Null when there is no offset to read from.
     */
    public String contextAt(int offset) {
        if (offset < 0 || offset >= context.length()) {
            return null;
        }
        Span span = spanAt(offset);
        int floor = span != null ? span.start() : 0;
        int ceiling = span != null ? span.end() : context.length();

        int start = Math.max(context.lastIndexOf('\n', offset) + 1, floor);
        for (int i = 0; i < Snippet.CONTEXT_LINES && start > floor; i++) {
            start = Math.max(context.lastIndexOf('\n', start - 2) + 1, floor);
        }

        int end = lineEnd(offset, ceiling);
        for (int i = 0; i < Snippet.CONTEXT_LINES && end < ceiling; i++) {
            end = lineEnd(end + 1, ceiling);
        }

        String window = context.substring(start, end).strip();
        return window.isEmpty() ? null : window;
    }

    private Span spanAt(int offset) {
        if (offset < 0) {
            return null;
        }
        for (Span span : spans) {
            if (offset >= span.start() && offset < span.end()) {
                return span;
            }
        }
        return null;
    }

    /** End of the line containing {@code offset}, never past {@code ceiling}. */
    private int lineEnd(int offset, int ceiling) {
        int newline = context.indexOf('\n', offset);
        return newline < 0 ? ceiling : Math.min(newline, ceiling);
    }
}
