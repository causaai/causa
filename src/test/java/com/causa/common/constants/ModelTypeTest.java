package com.causa.common.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ModelType Tests")
class ModelTypeTest {

    @ParameterizedTest(name = "provider={0}, model={1} -> {2}")
    @CsvSource({
        "vertex-ai-anthropic, claude-sonnet-4-6, VERTEX_AI_ANTHROPIC",
        "anthropic,           claude-3,          DIRECT_ANTHROPIC",
        "bob,                 granite-13b,        BOB",
        "ollama,              llama3,             OLLAMA",
        "unknown,             model,              VERTEX_AI_ANTHROPIC",
        "'',                  '',                 VERTEX_AI_ANTHROPIC",
    })
    void from_providerAndModel(String provider, String model, String expected) {
        ModelType result = ModelType.from(provider, model);
        assertThat(result.name()).isEqualTo(expected);
    }

    @Test void bobModelName_overridesProvider() {
        // Model name containing "granite" overrides any provider
        assertThat(ModelType.from("anthropic", "granite-something")).isEqualTo(ModelType.BOB);
    }

    @Test void bobInModelName_triggersBob() {
        assertThat(ModelType.from("any-provider", "some-bob-model")).isEqualTo(ModelType.BOB);
    }

    @Test void templateNames_areNonBlank() {
        for (ModelType t : ModelType.values()) {
            assertThat(t.getTemplateName()).isNotBlank();
        }
    }

    @Test void nullProvider_returnsDefault() {
        assertThat(ModelType.from(null, null)).isEqualTo(ModelType.VERTEX_AI_ANTHROPIC);
    }
}
