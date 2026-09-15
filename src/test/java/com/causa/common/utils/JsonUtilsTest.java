package com.causa.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JsonUtils Tests")
class JsonUtilsTest {

    @Nested
    @DisplayName("convertJsonStringToMap() Tests")
    class ConvertJsonStringToMapTests {

        @Test void nullReturnsEmpty()        { assertThat(JsonUtils.convertJsonStringToMap(null)).isEmpty(); }
        @Test void blankReturnsEmpty()       { assertThat(JsonUtils.convertJsonStringToMap("  ")).isEmpty(); }
        @Test void emptyObjectReturnsEmpty() { assertThat(JsonUtils.convertJsonStringToMap("{}")).isEmpty(); }

        @Test
        void validJsonParsed() {
            Map<String, String> result = JsonUtils.convertJsonStringToMap("{\"k\":\"v\",\"a\":\"b\"}");
            assertThat(result).containsEntry("k", "v").containsEntry("a", "b");
        }

        @Test
        void invalidJsonReturnsEmpty() {
            assertThat(JsonUtils.convertJsonStringToMap("not-json")).isEmpty();
        }
    }

    @Nested
    @DisplayName("mapToJsonNode() Tests")
    class MapToJsonNodeTests {

        @Test
        void nullMapReturnsEmptyObjectNode() {
            JsonNode node = JsonUtils.mapToJsonNode(null);
            assertThat(node.isObject()).isTrue();
            assertThat(node.size()).isZero();
        }

        @Test
        void emptyMapReturnsEmptyObjectNode() {
            assertThat(JsonUtils.mapToJsonNode(Map.of()).size()).isZero();
        }

        @Test
        void mapEntriesArePresent() {
            JsonNode node = JsonUtils.mapToJsonNode(Map.of("foo", "bar"));
            assertThat(node.get("foo").asText()).isEqualTo("bar");
        }
    }

    @Nested
    @DisplayName("jsonNodeToMap() Tests")
    class JsonNodeToMapTests {

        @Test
        void nullNodeReturnsEmpty() {
            assertThat(JsonUtils.jsonNodeToMap(null)).isEmpty();
        }

        @Test
        void nullJsonNodeReturnsEmpty() {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            assertThat(JsonUtils.jsonNodeToMap(mapper.nullNode())).isEmpty();
        }

        @Test
        void roundTrip() {
            Map<String, String> original = Map.of("x", "1", "y", "2");
            JsonNode node = JsonUtils.mapToJsonNode(original);
            Map<String, String> result = JsonUtils.jsonNodeToMap(node);
            assertThat(result).containsAllEntriesOf(original);
        }
    }

    @Nested
    @DisplayName("ConfigPropertyJsonConverter Tests")
    class ConverterTests {

        @Test
        void converterDelegatesToConvertJsonStringToMap() {
            JsonUtils.ConfigPropertyJsonConverter converter = new JsonUtils.ConfigPropertyJsonConverter();
            assertThat(converter.convert("{\"key\":\"val\"}")).containsEntry("key", "val");
        }

        @Test
        void converterReturnsEmptyForNull() {
            JsonUtils.ConfigPropertyJsonConverter converter = new JsonUtils.ConfigPropertyJsonConverter();
            assertThat(converter.convert(null)).isEmpty();
        }
    }
}
