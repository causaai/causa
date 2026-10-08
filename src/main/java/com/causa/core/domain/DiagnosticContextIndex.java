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
 * <p>Both validation paths reduce that text to something smaller — PATH B to a normalised
 * signal, PATH A to a snippet the LLM chose to quote — and neither reduction remembers where
 * it came from. This maps a position, or a quoted fragment, back to the source text around it,
 * so evidence can be read in situ instead of taken on trust.
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

    /**
     * Splits an LLM quote into fragments that might appear verbatim.
     *
     * <p>A quote is rarely contiguous source text: the model elides with an ellipsis and
     * appends its own commentary. The pieces between the elisions are the parts worth looking
     * for.
     */
    private static final Pattern ELISION = Pattern.compile("\\.{3}|…|\\R");

    /**
     * Shortest fragment worth locating. Below this a match is more likely coincidence than
     * provenance — {@code "heap"} occurs in every section and would anchor the window
     * anywhere at all.
     */
    private static final int MIN_FRAGMENT_LENGTH = 12;

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

    /**
     * The source text around a quoted fragment — what the quote was lifted out of.
     *
     * <p>Null when the quote cannot be located, and deliberately so. An LLM quote is not
     * guaranteed to be verbatim: it elides, paraphrases, and occasionally invents. Returning
     * an unrelated window because a short fragment happened to collide would manufacture
     * provenance for text that may never have been in the context at all.
     *
     * @param quote text the LLM cited as evidence; may be null
     * @return the surrounding source text, or null when the quote is not found in the context
     */
    public String contextAround(String quote) {
        return contextAt(offsetOf(quote));
    }

    /**
     * Locates a quote, preferring the longest fragment of it that appears verbatim.
     *
     * <p>Longest rather than first: a quote elided down to
     * {@code "alloc=2104786B ... retainedChunks=171"} has two anchors, and the more specific
     * one is the less likely to be a coincidental hit elsewhere in the context.
     */
    private int offsetOf(String quote) {
        if (quote == null || quote.isBlank()) {
            return -1;
        }

        int direct = context.indexOf(quote.strip());
        if (direct >= 0) {
            return direct;
        }

        int best = -1;
        int bestLength = 0;
        for (String fragment : ELISION.split(quote)) {
            String candidate = fragment.strip();
            if (candidate.length() < MIN_FRAGMENT_LENGTH || candidate.length() <= bestLength) {
                continue;
            }
            int at = context.indexOf(candidate);
            if (at >= 0) {
                best = at;
                bestLength = candidate.length();
            }
        }
        return best;
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
