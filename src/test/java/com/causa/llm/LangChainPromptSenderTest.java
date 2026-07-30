package com.causa.llm;

import com.causa.common.exceptions.LLMException;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.skills.Skills;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LangChainPromptSender Tests")
class LangChainPromptSenderTest {

    @Mock ChatModel chatModel;
    @Mock LLMConfig config;
    @Mock LLMConfig.SkillsConfig skillsConfig;
    @Mock Skills skills;

    // -----------------------------------------------------------------------
    // isReady()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("isReady() Tests")
    class IsReadyTests {

        @Test
        @DisplayName("returns true when chatModel non-null and modelName present")
        void ready_whenChatModelAndModelNamePresent() {
            when(config.modelName()).thenReturn(Optional.of("claude-3"));
            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);
            assertThat(sender.isReady()).isTrue();
        }

        @Test
        @DisplayName("returns false when modelName is empty")
        void notReady_whenModelNameEmpty() {
            when(config.modelName()).thenReturn(Optional.empty());
            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when modelName is blank")
        void notReady_whenModelNameBlank() {
            when(config.modelName()).thenReturn(Optional.of("   "));
            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when chatModel is null")
        void notReady_whenChatModelNull() {
            LangChainPromptSender sender = new LangChainPromptSender(null, config, skills);
            assertThat(sender.isReady()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // send() — not-ready path
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("send() when not ready")
    class SendNotReadyTests {

        @Test
        @DisplayName("throws LLMException when model not ready")
        void send_throwsLLMException_whenNotReady() {
            when(config.modelName()).thenReturn(Optional.empty());
            when(config.provider()).thenReturn(Optional.of("vertex-ai-anthropic"));
            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);

            LLMRequest request = LLMRequest.of("analyze this");
            assertThatThrownBy(() -> sender.send(request))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException with no provider configured")
        void send_throwsLLMException_noProvider() {
            when(config.modelName()).thenReturn(Optional.empty());
            when(config.provider()).thenReturn(Optional.empty());
            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);

            assertThatThrownBy(() -> sender.send(LLMRequest.of("test")))
                    .isInstanceOf(LLMException.class);
        }
    }

    // -----------------------------------------------------------------------
    // send() — ready path (ChatModel throws, caught as LLMException)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("send() when ready but ChatModel throws")
    class SendReadyButFailsTests {

        @Test
        @DisplayName("wraps ChatModel exception in LLMException")
        void send_wrapsException() {
            when(config.modelName()).thenReturn(Optional.of("claude-3"));
            when(config.provider()).thenReturn(Optional.of("anthropic"));
            when(config.skills()).thenReturn(skillsConfig);
            when(skillsConfig.enabled()).thenReturn(false);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("test prompt")))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("works with skills=null (no NPE)")
        void send_nullSkills_wrapsException() {
            when(config.modelName()).thenReturn(Optional.of("claude-3"));
            when(config.provider()).thenReturn(Optional.of("anthropic"));
            when(config.skills()).thenReturn(skillsConfig);
            when(skillsConfig.enabled()).thenReturn(false);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, null);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("prompt")))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("request with systemPrompt and context sent without NPE")
        void send_withSystemPromptAndContext() {
            when(config.modelName()).thenReturn(Optional.of("claude-3"));
            when(config.provider()).thenReturn(Optional.of("anthropic"));
            when(config.skills()).thenReturn(skillsConfig);
            when(skillsConfig.enabled()).thenReturn(false);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("server error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModel, config, skills);
            LLMRequest request = LLMRequest.builder("analyze this")
                    .systemPrompt("You are a helpful assistant")
                    .context("some k8s context")
                    .build();

            assertThatThrownBy(() -> sender.send(request))
                    .isInstanceOf(LLMException.class);
        }
    }
}
