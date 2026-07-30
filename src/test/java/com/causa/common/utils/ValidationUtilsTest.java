package com.causa.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ValidationUtils Tests")
class ValidationUtilsTest {

    @Nested
    @DisplayName("isValidInteger() Tests")
    class IsValidIntegerTests {
        @Test void nullReturnsFalse()  { assertThat(ValidationUtils.isValidInteger(null)).isFalse(); }
        @Test void blankReturnsFalse() { assertThat(ValidationUtils.isValidInteger("  ")).isFalse(); }
        @ParameterizedTest @ValueSource(strings = {"0", "1", "-1", "100", "2147483647"})
        void validIntegersReturnTrue(String v) { assertThat(ValidationUtils.isValidInteger(v)).isTrue(); }
        @ParameterizedTest @ValueSource(strings = {"1.5", "abc", "1e3", ""})
        void invalidIntegersReturnFalse(String v) { assertThat(ValidationUtils.isValidInteger(v)).isFalse(); }
    }

    @Nested
    @DisplayName("isValidDouble() Tests")
    class IsValidDoubleTests {
        @Test void nullReturnsFalse()  { assertThat(ValidationUtils.isValidDouble(null)).isFalse(); }
        @Test void blankReturnsFalse() { assertThat(ValidationUtils.isValidDouble("  ")).isFalse(); }
        @ParameterizedTest @ValueSource(strings = {"0.0", "1.5", "-3.14", "100", "1e3"})
        void validDoublesReturnTrue(String v) { assertThat(ValidationUtils.isValidDouble(v)).isTrue(); }
        @ParameterizedTest @ValueSource(strings = {"abc", "1,5", ""})
        void invalidDoublesReturnFalse(String v) { assertThat(ValidationUtils.isValidDouble(v)).isFalse(); }
    }

    @Nested
    @DisplayName("isValidBoolean() Tests")
    class IsValidBooleanTests {
        @Test void nullReturnsFalse()  { assertThat(ValidationUtils.isValidBoolean(null)).isFalse(); }
        @Test void blankReturnsFalse() { assertThat(ValidationUtils.isValidBoolean("  ")).isFalse(); }
        @ParameterizedTest @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False"})
        void validBooleansReturnTrue(String v) { assertThat(ValidationUtils.isValidBoolean(v)).isTrue(); }
        @ParameterizedTest @ValueSource(strings = {"yes", "no", "1", "0", "on", "off"})
        void invalidBooleansReturnFalse(String v) { assertThat(ValidationUtils.isValidBoolean(v)).isFalse(); }
    }
}
