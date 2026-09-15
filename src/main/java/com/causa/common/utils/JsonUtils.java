package com.causa.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.config.spi.Converter;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
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
     * Converts a JSON string into a {@code Map<String, String>} for programmatic use.
     *
     * <p>Returns an empty map if the input is null, blank, or {@code "{}"}.
     * Logs a warning and returns an empty map if parsing fails.
     *
     * <p><b>Use Cases:</b> Manual JSON parsing in code (API responses, user input, dynamic config, testing).
     *
     * @param json the JSON string to convert
     * @return a map of key-value pairs, or an empty map if parsing fails
     */
    public static Map<String, String> convertJsonStringToMap(String json) {
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
     * Converts Map&lt;String, String&gt; to JsonNode for JSONB database storage.
     * Returns an empty ObjectNode (not null) for null or empty maps so that
     * JSONB columns always store a valid JSON object.
     *
     * @param map the map to convert
     * @return JsonNode representation, or empty ObjectNode if map is null/empty
     */
    public static JsonNode mapToJsonNode(Map<String, String> map) {
        ObjectNode jsonNode = OBJECT_MAPPER.createObjectNode();
        if (map != null) {
            map.forEach(jsonNode::put);
        }
        return jsonNode;
    }

    /**
     * Converts JsonNode to Map&lt;String, String&gt; for domain models.
     *
     * @param jsonNode the JsonNode to convert
     * @return Map representation, or empty map if jsonNode is null
     */
    public static Map<String, String> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        jsonNode.fields().forEachRemaining(entry ->
            map.put(entry.getKey(), entry.getValue().asText())
        );
        return map;
    }

    /**
     * MicroProfile Config converter for automatic JSON-to-Map conversion in configuration properties.
     *
     * <p>Enables binding environment variables like {@code LLM_CUSTOM_HEADERS='{"X-Gateway":"ibm-bob"}'}
     * directly to {@code Map<String, String>} configuration properties.
     *
     * <p><b>Use Cases:</b> Declarative config injection (env vars, application.yml, ConfigMaps, Secrets).
     *
     * <p>Delegates to {@link #convertJsonStringToMap(String)} for parsing logic.
     *
     * @since 0.0.1
     */
    public static class ConfigPropertyJsonConverter implements Converter<Map<String, String>> {

        @Override
        public Map<String, String> convert(String value) throws IllegalArgumentException {
            return convertJsonStringToMap(value);
        }
    }
}
