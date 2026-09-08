package com.causa.common.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonParsingConstants#JSON_OBJECT_PATTERN}.
 *
 * <p>The regex must handle every LLM output shape produced by this PR:
 * <ol>
 *   <li>Plain JSON (no fences, no prose)</li>
 *   <li>JSON wrapped in a markdown code fence (any language tag)</li>
 *   <li>Leading prose before the JSON object</li>
 *   <li>Trailing prose / closing fence after the JSON object</li>
 *   <li>Combinations of the above</li>
 *   <li>No-match cases (no {@code {}} pair present)</li>
 * </ol>
 *
 * @since 0.0.1
 */
@DisplayName("JsonParsingConstants.JSON_OBJECT_PATTERN Tests")
class JsonParsingConstantsTest {

    /** Convenience: run the pattern and return group 1, or null if no match. */
    private static String extract(String input) {
        Matcher m = JsonParsingConstants.JSON_OBJECT_PATTERN.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static boolean matches(String input) {
        return JsonParsingConstants.JSON_OBJECT_PATTERN.matcher(input).find();
    }

    // -------------------------------------------------------------------------
    // Plain JSON — no fences, no prose
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Plain JSON")
    class PlainJsonTests {

        @Test
        @DisplayName("Single-key object matches and is returned as-is")
        void singleKeyObject() {
            assertThat(extract("{\"key\":\"val\"}")).isEqualTo("{\"key\":\"val\"}");
        }

        @Test
        @DisplayName("Multi-key object matches")
        void multiKeyObject() {
            String json = "{\"a\":1,\"b\":\"two\"}";
            assertThat(extract(json)).isEqualTo(json);
        }

        @Test
        @DisplayName("Nested object is retained in full by greedy .*")
        void nestedObjectRetainedByGreedy() {
            String json = "{\"outer\":{\"inner\":\"value\"}}";
            assertThat(extract(json)).isEqualTo(json);
        }

        @Test
        @DisplayName("Multiline JSON object is matched (DOTALL)")
        void multilineJson() {
            String json = "{\n  \"key\": \"value\",\n  \"num\": 42\n}";
            assertThat(extract(json)).isEqualTo(json);
        }

        @Test
        @DisplayName("String value containing '}' is retained — greedy .*} stops at the last brace")
        void stringValueContainingClosingBrace() {
            // Greedy .*} always grabs the rightmost '}', so a '}' inside a string value
            // does not prematurely terminate the match. This guards against someone
            // switching the quantifier to non-greedy (.*?}).
            String json = "{\"summary\":\"Container exited with code 137 (OOM}killed)\",\"confidence\":0.9}";
            assertThat(extract(json)).isEqualTo(json);
        }

        @Test
        @DisplayName("Multiple string values containing '}' are all retained")
        void multipleStringValuesContainingClosingBrace() {
            // Both the rootCause and recommendation fields contain '}' inside their text.
            // Non-greedy .*? would stop at the first '}' and return broken JSON.
            String json = "{\"rootCause\":\"Memory limit breached (limit: 512Mi})\",\"recommendation\":\"Increase memory limit (e.g. 1Gi})\"}";
            assertThat(extract(json)).isEqualTo(json);
        }
    }

    // -------------------------------------------------------------------------
    // Markdown code fences
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Markdown code fences")
    class CodeFenceTests {

        @Test
        @DisplayName("```json fence is stripped and inner JSON returned")
        void jsonFence() {
            String input = "```json\n{\"key\":\"val\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"key\":\"val\"}");
        }

        @Test
        @DisplayName("Generic ``` fence (no language tag) is stripped")
        void genericFence() {
            String input = "```\n{\"key\":\"val\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"key\":\"val\"}");
        }

        @ParameterizedTest(name = "fence language tag: {0}")
        @ValueSource(strings = {"JSON", "Json", "json5", "javascript", "text"})
        @DisplayName("Any language tag after ``` is handled")
        void arbitraryLanguageTag(String langTag) {
            String input = "```" + langTag + "\n{\"k\":\"v\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"k\":\"v\"}");
        }

        @Test
        @DisplayName("Closing fence is excluded from group 1")
        void closingFenceExcludedFromGroup1() {
            String input = "```json\n{\"k\":\"v\"}\n```";
            assertThat(extract(input)).doesNotContain("```");
        }
    }

    // -------------------------------------------------------------------------
    // Leading prose preamble
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Leading prose preamble")
    class LeadingProseTests {

        @Test
        @DisplayName("Single prose line before JSON is stripped")
        void singleProseLine() {
            String input = "Here is the JSON:\n{\"key\":\"val\"}";
            assertThat(extract(input)).isEqualTo("{\"key\":\"val\"}");
        }

        @Test
        @DisplayName("Real post-tool LLM prologue is stripped")
        void postToolPrologueVariant() {
            // This is the exact preamble the LLM emits after tool calls — the scenario this PR fixes
            String input = "Now let me analyse the collected data and provide the RCA.\n{\"status\":\"ok\",\"summary\":\"OOM\"}";
            assertThat(extract(input)).isEqualTo("{\"status\":\"ok\",\"summary\":\"OOM\"}");
        }

        @Test
        @DisplayName("Multi-sentence prose preamble is stripped")
        void multiSentenceProse() {
            String input = "Based on the skill context. Let me now respond.\n{\"a\":1}";
            assertThat(extract(input)).isEqualTo("{\"a\":1}");
        }
    }

    // -------------------------------------------------------------------------
    // Trailing prose / closing fence
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Trailing prose and closing fences")
    class TrailingProseTests {

        @Test
        @DisplayName("Trailing prose after closing brace is excluded (greedy .*})")
        void trailingProseExcluded() {
            String input = "{\"key\":\"val\"}\nLet me explain the above result...";
            assertThat(extract(input)).isEqualTo("{\"key\":\"val\"}");
        }

        @Test
        @DisplayName("Closing ``` after JSON is excluded")
        void closingFenceExcluded() {
            String input = "{\"key\":\"val\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"key\":\"val\"}");
        }
    }

    // -------------------------------------------------------------------------
    // Combined — fence + prose
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Combined: fence + leading prose")
    class CombinedTests {

        @Test
        @DisplayName("Prose before opening fence: fence not present, [^{]* skips prose")
        void proseBeforeOpeningFence() {
            // Optional fence only fires when the *very first characters* are ```.
            // When prose comes first the [^{]* part handles everything before '{'.
            String input = "Sure! Here it is:\n```json\n{\"k\":\"v\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"k\":\"v\"}");
        }

        @Test
        @DisplayName("Leading prose + trailing prose: only JSON object returned")
        void leadingAndTrailingProse() {
            String input = "Here you go:\n{\"result\":\"pass\"}\nThat's the output.";
            assertThat(extract(input)).isEqualTo("{\"result\":\"pass\"}");
        }

        @Test
        @DisplayName("Prose + inline fence on same line: [^{]* absorbs fence chars, JSON extracted")
        void proseFollowedByInlineFenceAndJson() {
            // The optional fence group only fires when ``` opens the string.
            // When prose precedes the fence, [^{]* consumes everything up to '{',
            // including the backticks and language tag.
            String input = "Here is the result for you ```json {\"key\":\"value\"}```";
            assertThat(extract(input)).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        @DisplayName("Prose + fenced block with newlines: same [^{]* absorption, JSON extracted")
        void proseFollowedByFencedBlockWithNewlines() {
            // Same prose-before-fence scenario but with the conventional newline form.
            String input = "Here is the result for you ```json\n{\"key\":\"value\"}\n```";
            assertThat(extract(input)).isEqualTo("{\"key\":\"value\"}");
        }
    }

    // -------------------------------------------------------------------------
    // No-match cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("No-match cases (no JSON object present)")
    class NoMatchTests {

        @Test
        @DisplayName("Plain prose with no braces returns no match")
        void plainProseNoMatch() {
            assertThat(matches("No JSON here at all")).isFalse();
        }

        @Test
        @DisplayName("Empty string returns no match")
        void emptyStringNoMatch() {
            assertThat(matches("")).isFalse();
        }

        @Test
        @DisplayName("Open brace only (no closing brace) returns no match")
        void openBraceNoClosingBrace() {
            // Pattern requires both { and } — incomplete JSON must not match
            assertThat(matches("{\"incomplete\"")).isFalse();
        }

        @Test
        @DisplayName("Array literal (no object braces) returns no match")
        void arrayLiteralNoMatch() {
            assertThat(matches("[\"a\",\"b\"]")).isFalse();
        }
    }
}
