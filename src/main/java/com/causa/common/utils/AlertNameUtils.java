package com.causa.common.utils;

import com.causa.common.constants.AlertConstants;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.Instant;

/**
 * Alert Name Generation Utilities.
 *
 * <p>Provides helpers for constructing synthetic alert names used by the manual trigger path.
 *
 * @since 0.0.1
 */
public final class AlertNameUtils {

    private AlertNameUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Generates a unique alert name for a manually triggered diagnostic.
     *
     * <p>Format: {@code manual-analysis-trigger-<epoch-seconds>-<3-char-alphanumeric>}
     * <p>Example: {@code manual-analysis-trigger-1722000000-aB3}
     *
     * @return the generated alert name
     */
    public static String generateManualAlertName() {
        long epochSeconds = Instant.now().getEpochSecond();
        String randomSuffix = RandomStringUtils.secure().nextAlphanumeric(3);
        return AlertConstants.ManualTrigger.ALERT_NAME_PREFIX
                + "-" + epochSeconds
                + "-" + randomSuffix;
    }
}
