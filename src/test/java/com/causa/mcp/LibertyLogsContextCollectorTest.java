package com.causa.mcp;

import com.causa.config.McpConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LibertyLogsContextCollector Pure-Logic Tests")
class LibertyLogsContextCollectorTest {

    @Mock McpConfig mcpConfig;
    @Mock McpConfig.FilesystemConfig filesystemConfig;

    LibertyLogsContextCollector collector;

    @BeforeEach
    void setUp() {
        when(mcpConfig.filesystem()).thenReturn(filesystemConfig);
        // Use a real instance — no HTTP client needed for pure-logic tests
        collector = new LibertyLogsContextCollector(mcpConfig);
    }

    // -----------------------------------------------------------------------
    // filterFfdcContent()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("filterFfdcContent() Tests")
    class FilterFfdcTests {

        @Test
        @DisplayName("returns raw when no dump section present")
        void noDumpSection_returnsRaw() {
            String raw = "Exception: OOM\nat com.example.Foo.bar(Foo.java:42)";
            assertThat(collector.filterFfdcContent(raw)).isEqualTo(raw);
        }

        @Test
        @DisplayName("truncates at Dump of callerThis section")
        void truncatesAtDumpSection() {
            String raw = "Exception header\n\nDump of callerThis\nfield1=value1\nfield2=value2";
            String result = collector.filterFfdcContent(raw);
            assertThat(result).contains("Exception header");
            assertThat(result).doesNotContain("field1=value1");
            assertThat(result).contains("ffdc object dump section omitted");
        }

        @Test
        @DisplayName("handles empty string")
        void emptyString_returnsEmpty() {
            assertThat(collector.filterFfdcContent("")).isEmpty();
        }

        @Test
        @DisplayName("handles content with dump marker at start")
        void dumpAtStart() {
            String raw = "\nDump of callerThis\nstuff";
            String result = collector.filterFfdcContent(raw);
            assertThat(result).contains("ffdc object dump section omitted");
            assertThat(result).doesNotContain("stuff");
        }
    }

    // -----------------------------------------------------------------------
    // filterMessagesLogContent()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("filterMessagesLogContent() Tests")
    class FilterMessagesLogTests {

        @Test
        @DisplayName("keeps plain-text header lines always")
        void keepsPlainTextHeaderLines() {
            String raw = "Open Liberty 24.0.0\nProduct installed at /opt/ibm/wlp\n";
            String result = collector.filterMessagesLogContent(raw);
            assertThat(result).contains("Open Liberty 24.0.0");
        }

        @Test
        @DisplayName("keeps SEVERE JSON lines")
        void keepsSevereLines() {
            String severe = "{\"loglevel\":\"SEVERE\",\"message\":\"OutOfMemoryError\"}";
            assertThat(collector.filterMessagesLogContent(severe + "\n")).contains(severe);
        }

        @Test
        @DisplayName("keeps WARNING JSON lines")
        void keepsWarningLines() {
            String warn = "{\"loglevel\":\"WARNING\",\"message\":\"High heap usage\"}";
            assertThat(collector.filterMessagesLogContent(warn + "\n")).contains(warn);
        }

        @Test
        @DisplayName("drops INFO JSON lines")
        void dropsInfoLines() {
            String info = "{\"loglevel\":\"INFO\",\"message\":\"Server started\"}";
            String result = collector.filterMessagesLogContent(info + "\n");
            assertThat(result).doesNotContain("Server started");
            assertThat(result).contains("dropped");
        }

        @Test
        @DisplayName("drops AUDIT JSON lines")
        void dropsAuditLines() {
            String audit = "{\"loglevel\":\"AUDIT\",\"message\":\"Feature loaded\"}";
            String result = collector.filterMessagesLogContent(audit + "\n");
            assertThat(result).doesNotContain("Feature loaded");
        }

        @Test
        @DisplayName("mixed content: keeps header + SEVERE, drops INFO")
        void mixedContent() {
            String raw = "Liberty header\n"
                    + "{\"loglevel\":\"INFO\",\"message\":\"startup\"}\n"
                    + "{\"loglevel\":\"SEVERE\",\"message\":\"OOM\"}\n";
            String result = collector.filterMessagesLogContent(raw);
            assertThat(result).contains("Liberty header");
            assertThat(result).contains("OOM");
            assertThat(result).doesNotContain("startup");
        }

        @Test
        @DisplayName("no filtered lines means no summary comment appended")
        void noDropped_noSummaryComment() {
            String raw = "plain header line only\n";
            assertThat(collector.filterMessagesLogContent(raw)).doesNotContain("messages filtered");
        }
    }

    // -----------------------------------------------------------------------
    // filterVerboseGcContent()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("filterVerboseGcContent() Tests")
    class FilterVerboseGcTests {

        @BeforeEach
        void setWindow() {
            when(filesystemConfig.alertWindowMinutes()).thenReturn(5);
        }

        @Test
        @DisplayName("returns header + end tag when no GC blocks present")
        void noGcBlocks_returnsHeader() {
            String raw = "<verbosegc>\n<initialized><heap/></initialized>\n</verbosegc>\n";
            String result = collector.filterVerboseGcContent(raw, Instant.now());
            assertThat(result).contains("<verbosegc>\n");
            assertThat(result).contains("</verbosegc>");
        }

        @Test
        @DisplayName("retains global GC block regardless of timestamp")
        void globalGcBlock_alwaysRetained() {
            // Instant.parse requires ISO-8601 with timezone (Z suffix)
            String ts = "2024-01-15T10:30:00Z";
            String raw = "<verbosegc>\n</initialized>\n"
                    + "<exclusive-start timestamp=\"" + ts + "\" />\n"
                    + "<gc type=\"global\" />\n"
                    + "<exclusive-end timestamp=\"" + ts + "\" />\n";
            // Alert time far from block — still retained because global
            String result = collector.filterVerboseGcContent(raw, Instant.parse("2024-01-15T12:00:00Z"));
            assertThat(result).contains("exclusive-start");
        }

        @Test
        @DisplayName("drops non-global block outside time window")
        void nonGlobalOutsideWindow_dropped() {
            String ts = "2024-01-15T08:00:00Z"; // far outside 5-min window, must have Z suffix
            String raw = "<verbosegc>\n</initialized>\n"
                    + "<exclusive-start timestamp=\"" + ts + "\" />\n"
                    + "<exclusive-end timestamp=\"" + ts + "\" />\n";
            String result = collector.filterVerboseGcContent(raw, Instant.parse("2024-01-15T10:30:00Z"));
            // Block should be dropped
            assertThat(result).doesNotContain("exclusive-start");
        }

        @Test
        @DisplayName("retains block inside time window")
        void blockInsideWindow_retained() {
            Instant alertTime = Instant.parse("2024-01-15T10:30:00Z");
            // Block timestamp 1 minute before alert — within 5-min window; must have Z suffix for Instant.parse
            String ts = "2024-01-15T10:29:00Z";
            String raw = "<verbosegc>\n</initialized>\n"
                    + "<exclusive-start timestamp=\"" + ts + "\" />\n"
                    + "<exclusive-end timestamp=\"" + ts + "\" />\n";
            String result = collector.filterVerboseGcContent(raw, alertTime);
            assertThat(result).contains("exclusive-start");
        }

        @Test
        @DisplayName("handles empty raw input gracefully")
        void emptyInput_noThrow() {
            assertThatCode(() -> collector.filterVerboseGcContent("", Instant.now()))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // collectLibertyLogs() — HTTP failure path (no server reachable)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("collectLibertyLogs() HTTP failure path")
    class CollectLibertyLogsFailureTests {

        @Test
        @DisplayName("returns null when HTTP endpoint unreachable")
        void returnsNull_whenHttpFails() {
            when(filesystemConfig.libertyLogsDir()).thenReturn("/logs");
            when(filesystemConfig.timeoutMs()).thenReturn(1);
            when(filesystemConfig.endpoint()).thenReturn("http://192.0.2.1"); // TEST-NET, unreachable
            when(filesystemConfig.alertWindowMinutes()).thenReturn(5);

            String result = collector.collectLibertyLogs("alert-1", Instant.now());
            assertThat(result).isNull();
        }
    }
}
