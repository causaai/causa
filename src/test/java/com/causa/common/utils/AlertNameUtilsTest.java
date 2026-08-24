package com.causa.common.utils;

import com.causa.common.constants.AlertConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlertNameUtils}.
 *
 * @since 0.0.1
 */
@DisplayName("AlertNameUtils Tests")
class AlertNameUtilsTest {

    // -------------------------------------------------------------------------
    // generateManualAlertName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return a non-null, non-blank alert name")
    void shouldReturnNonNullNonBlankName() {
        String name = AlertNameUtils.generateManualAlertName();

        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    @DisplayName("Should start with the expected prefix from AlertConstants")
    void shouldStartWithManualTriggerPrefix() {
        String name = AlertNameUtils.generateManualAlertName();

        assertTrue(name.startsWith(AlertConstants.ManualTrigger.ALERT_NAME_PREFIX),
                "Expected name to start with '" + AlertConstants.ManualTrigger.ALERT_NAME_PREFIX
                + "' but was: " + name);
    }

    @Test
    @DisplayName("Should contain epoch seconds segment after the prefix")
    void shouldContainEpochSecondsSegment() {
        String name = AlertNameUtils.generateManualAlertName();
        // Format: manual-analysis-trigger-<epoch>-<suffix>
        String[] parts = name.split("-");
        // prefix has 3 words joined by '-': manual, analysis, trigger → parts[0..2]
        // then epoch at parts[3], then 3-char suffix at parts[4]
        assertTrue(parts.length >= 5, "Expected at least 5 dash-separated segments, got: " + name);

        String epochPart = parts[3];
        assertDoesNotThrow(() -> Long.parseLong(epochPart),
                "Expected epoch-seconds segment to be numeric but was: " + epochPart);
    }

    @Test
    @DisplayName("Should append a 3-character alphanumeric suffix")
    void shouldAppendThreeCharSuffix() {
        String name = AlertNameUtils.generateManualAlertName();
        String suffix = name.substring(name.lastIndexOf('-') + 1);

        assertEquals(3, suffix.length(), "Expected 3-char suffix but got: " + suffix);
        assertTrue(suffix.matches("[A-Za-z0-9]{3}"),
                "Expected alphanumeric suffix but got: " + suffix);
    }

    @RepeatedTest(20)
    @DisplayName("Should produce unique names across repeated calls")
    void shouldProduceUniqueNames() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            names.add(AlertNameUtils.generateManualAlertName());
        }
        // With a random 3-char suffix (62^3 = 238,328 combinations) + epoch seconds,
        // collisions within a single second are astronomically unlikely.
        assertTrue(names.size() > 1,
                "Expected unique names across repeated calls but all were identical");
    }

    // -------------------------------------------------------------------------
    // Utility class instantiation guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should throw AssertionError when instantiated via reflection")
    void shouldThrowOnInstantiation() throws Exception {
        var constructor = AlertNameUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                constructor::newInstance);
        assertInstanceOf(AssertionError.class, ex.getCause());
    }
}
