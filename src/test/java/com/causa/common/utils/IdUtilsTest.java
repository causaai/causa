package com.causa.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IdUtils Tests")
class IdUtilsTest {

    @Nested
    @DisplayName("generateAlertId() Tests")
    class AlertIdTests {
        @Test void hasCorrectPrefix()   { assertThat(IdUtils.generateAlertId()).startsWith("alrt_"); }
        @Test void hasCorrectLength()   { assertThat(IdUtils.generateAlertId()).hasSize(21); }
        @Test void isAlphanumericSuffix() {
            assertThat(IdUtils.generateAlertId().substring(5)).matches("[a-zA-Z0-9]{16}");
        }
        @RepeatedTest(5)
        void isUnique() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) ids.add(IdUtils.generateAlertId());
            assertThat(ids).hasSize(100);
        }
    }

    @Nested
    @DisplayName("generateDiagnosticId() Tests")
    class DiagnosticIdTests {
        @Test void hasCorrectPrefix() { assertThat(IdUtils.generateDiagnosticId()).startsWith("diag_"); }
        @Test void hasCorrectLength() { assertThat(IdUtils.generateDiagnosticId()).hasSize(21); }
        @Test void isAlphanumericSuffix() {
            assertThat(IdUtils.generateDiagnosticId().substring(5)).matches("[a-zA-Z0-9]{16}");
        }
    }

    @Nested
    @DisplayName("generateConfigurationId() Tests")
    class ConfigurationIdTests {
        @Test void hasCorrectPrefix() { assertThat(IdUtils.generateConfigurationId()).startsWith("cnfg_"); }
        @Test void hasCorrectLength() { assertThat(IdUtils.generateConfigurationId()).hasSize(21); }
        @Test void isAlphanumericSuffix() {
            assertThat(IdUtils.generateConfigurationId().substring(5)).matches("[a-zA-Z0-9]{16}");
        }
    }
}
