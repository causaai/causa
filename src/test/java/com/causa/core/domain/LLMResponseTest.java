package com.causa.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LLMResponse Tests")
class LLMResponseTest {

    LLMResponse response = new LLMResponse("text", "claude-3", 100, 50, 10, 20, 3200);

    @Test void getters() {
        assertThat(response.responseText()).isEqualTo("text");
        assertThat(response.modelUsed()).isEqualTo("claude-3");
        assertThat(response.inputTokens()).isEqualTo(100);
        assertThat(response.outputTokens()).isEqualTo(50);
        assertThat(response.cacheCreationTokens()).isEqualTo(10);
        assertThat(response.cacheReadTokens()).isEqualTo(20);
        assertThat(response.latencyMs()).isEqualTo(3200);
    }

    @Nested @DisplayName("wasCacheHit() Tests")
    class CacheHitTests {
        @Test void cacheReadTokensGtZero_isTrue()  { assertThat(response.wasCacheHit()).isTrue(); }
        @Test void zeroCacheReadTokens_isFalse() {
            assertThat(new LLMResponse("t","m",100,50,0,0,0).wasCacheHit()).isFalse();
        }
    }

    @Nested @DisplayName("totalInputTokens() Tests")
    class TotalInputTests {
        @Test void sumIsCorrect() {
            // 100 + 10 + 20 = 130
            assertThat(response.totalInputTokens()).isEqualTo(130);
        }
        @Test void zeroCacheTokens() {
            assertThat(new LLMResponse("t","m",100,0,0,0,0).totalInputTokens()).isEqualTo(100);
        }
    }
}
