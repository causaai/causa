package com.causa.infrastructure.health;

import com.causa.core.ports.llm.PromptSender;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LLMHealthCheck Tests")
class LLMHealthCheckTest {

    @Mock PromptSender promptSender;
    @InjectMocks LLMHealthCheck llmHealthCheck;

    @Nested @DisplayName("call() when LLM is ready")
    class WhenReady {
        @Test void returnsUp() {
            when(promptSender.isReady()).thenReturn(true);
            assertThat(llmHealthCheck.call().getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        }
        @Test void hasName() {
            when(promptSender.isReady()).thenReturn(true);
            assertThat(llmHealthCheck.call().getName()).isNotBlank();
        }
    }

    @Nested @DisplayName("call() when LLM is not ready")
    class WhenNotReady {
        @Test void returnsDown() {
            when(promptSender.isReady()).thenReturn(false);
            assertThat(llmHealthCheck.call().getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        }
    }
}
