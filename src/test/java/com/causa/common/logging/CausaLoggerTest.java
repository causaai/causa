package com.causa.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CausaLogger Tests")
class CausaLoggerTest {

    @Nested @DisplayName("getLogger() Factory Tests")
    class FactoryTests {
        @Test void byClass()  { assertThat(CausaLogger.getLogger(CausaLoggerTest.class)).isNotNull(); }
        @Test void byName()   { assertThat(CausaLogger.getLogger("test.logger")).isNotNull(); }
    }

    @Nested @DisplayName("LogBuilder Tests")
    class LogBuilderTests {

        @Test void info_noException_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .info("test message")
                    .field("key", "value")
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void debug_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .debug("debug msg")
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void warn_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .warn("warn msg")
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void error_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .error("error msg")
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void withException_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .error("error")
                    .exception(new RuntimeException("boom"))
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void withMultipleFields_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .info("multi fields")
                    .field("str", "value")
                    .field("num", 42)
                    .field("bool", true)
                    .field("null", (Object) null)
                    .field("instant", Instant.now())
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void withFieldsMap_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .info("map fields")
                    .fields(Map.of("a", "1", "b", "2"))
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void withNullFieldsMap_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .info("null map")
                    .fields(null)
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void debugWithException_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .debug("debug with ex")
                    .exception(new IllegalStateException("state"))
                    .log()
            ).doesNotThrowAnyException();
        }

        @Test void warnWithException_doesNotThrow() {
            assertThatCode(() ->
                CausaLogger.getLogger(CausaLoggerTest.class)
                    .warn("warn with ex")
                    .exception(new RuntimeException("warn ex"))
                    .log()
            ).doesNotThrowAnyException();
        }
    }
}
