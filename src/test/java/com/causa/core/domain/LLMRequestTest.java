package com.causa.core.domain;

import com.causa.common.exceptions.LLMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LLMRequest Tests")
class LLMRequestTest {

    @Nested @DisplayName("of() Factory Tests")
    class OfTests {
        @Test void promptOnly_setsPrompt() {
            LLMRequest r = LLMRequest.of("hello");
            assertThat(r.prompt()).isEqualTo("hello");
            assertThat(r.systemPrompt()).isEmpty();
            assertThat(r.context()).isEmpty();
        }
        @Test void blankPromptThrows() {
            assertThatThrownBy(() -> LLMRequest.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }
        @Test void nullPromptThrows() {
            assertThatThrownBy(() -> LLMRequest.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("Builder Tests")
    class BuilderTests {
        @Test void allFieldsSet() {
            LLMRequest r = LLMRequest.builder("prompt")
                .systemPrompt("sys")
                .context("ctx")
                .modelOverride("claude-3")
                .enableCaching(true)
                .enableSkills(false)
                .maxTokens(1000)
                .temperature(0.5)
                .build();
            assertThat(r.systemPrompt()).contains("sys");
            assertThat(r.context()).contains("ctx");
            assertThat(r.modelOverride()).contains("claude-3");
            assertThat(r.enableCaching()).contains(true);
            assertThat(r.enableSkills()).contains(false);
            assertThat(r.maxTokens()).contains(1000);
            assertThat(r.temperature()).contains(0.5);
        }

        @Test void nullSystemPromptBecomesEmpty() {
            LLMRequest r = LLMRequest.builder("prompt").systemPrompt(null).build();
            assertThat(r.systemPrompt()).isEmpty();
        }

        @Test void temperatureOutOfRange_throws() {
            assertThatThrownBy(() -> LLMRequest.builder("p").temperature(2.0).build())
                .isInstanceOf(LLMException.class);
        }

        @Test void negativTemperature_throws() {
            assertThatThrownBy(() -> LLMRequest.builder("p").temperature(-0.1).build())
                .isInstanceOf(LLMException.class);
        }

        @Test void maxTokensTooLow_throws() {
            assertThatThrownBy(() -> LLMRequest.builder("p").maxTokens(0).build())
                .isInstanceOf(LLMException.class);
        }

        @Test void validTemperatureBoundaries() {
            assertThatCode(() -> LLMRequest.builder("p").temperature(0.0).build()).doesNotThrowAnyException();
            assertThatCode(() -> LLMRequest.builder("p").temperature(1.0).build()).doesNotThrowAnyException();
        }

        @Test void validMaxTokens() {
            assertThatCode(() -> LLMRequest.builder("p").maxTokens(1).build()).doesNotThrowAnyException();
        }
    }
}
