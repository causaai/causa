package com.causa.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.spi.Converter;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Map;

/**
 * JSON utility class for serialization, deserialization, and configuration conversions.
 *
 * <p>Provides utility methods for JSON operations and implements SmallRye Config converter
 * for parsing JSON strings into {@code Map<String, String>}.
 *
 * <p><b>Thread Safety:</b> The ObjectMapper instance is thread-safe and reused across calls.
 *
 * @since 0.0.1
 */
public final class JsonUtils {

    private static final Logger LOG = Logger.getLogger(JsonUtils.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Private constructor to prevent instantiation.
     */
    private JsonUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Parses a JSON string into a {@code Map<String, String>}.
     *
     * <p>Returns an empty map if the input is null, blank, or {@code "{}"}.
     * Logs a warning and returns an empty map if parsing fails.
     *
     * <p><b>Use Case:</b> Parsing environment variables like
     * {@code LLM_CUSTOM_HEADERS='{"X-Gateway":"ibm-bob","X-Tenant":"prod"}'}.
     *
     * @param json the JSON string to parse
     * @return a map of key-value pairs, or an empty map if parsing fails
     */
    public static Map<String, String> parseJsonToMap(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Collections.emptyMap();
        }

        try {
            TypeReference<Map<String, String>> typeRef = new TypeReference<>() {};
            Map<String, String> result = OBJECT_MAPPER.readValue(json, typeRef);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            LOG.warnf("Failed to parse JSON string to Map<String, String>: %s. Returning empty map. Error: %s",
                    json, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * SmallRye Config converter for parsing JSON strings into {@code Map<String, String>}.
     *
     * <p>Enables binding environment variables like {@code LLM_CUSTOM_HEADERS='{"X-Gateway":"ibm-bob"}'}
     * directly to {@code Map<String, String>} configuration properties.
     *
     * <p>Delegates to {@link #parseJsonToMap(String)} for parsing logic.
     *
     * @since 0.0.1
     */
    public static class JsonMapConverter implements Converter<Map<String, String>> {

        @Override
        public Map<String, String> convert(String value) throws IllegalArgumentException {
            return parseJsonToMap(value);
        }
    }
}
