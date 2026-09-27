package com.causa.llm;

import com.causa.common.constants.ConfigConstants.LlmProvider;
import com.causa.common.exceptions.LLMException;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LlmConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.skills.Skills;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LangChainPromptSender Tests")
class LangChainPromptSenderTest {

    @Mock ChatModelFactory chatModelFactory;
    @Mock ChatModel chatModel;
    @Mock LlmConfigCache llmConfigCache;
    @Mock Skills skills;

    private LlmConfig activeConfig(String model) {
        return LlmConfig.builder()
            .id("llm_cnf_test")
            .provider(LlmProvider.ANTHROPIC)
            .url("https://api.example.com")
            .models(model != null && !model.isBlank() ? List.of(model) : List.of())
            .authConfig(new AuthConfig(null, null, null, null, null, null, null, null))
            .build();
    }

    // -----------------------------------------------------------------------
    // isReady()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("isReady() Tests")
    class IsReadyTests {

        @Test
        @DisplayName("returns true when chatModelFactory non-null and model present")
        void ready_whenChatModelFactoryAndModelPresent() {
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("claude-3")));
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            assertThat(sender.isReady()).isTrue();
        }

        @Test
        @DisplayName("returns false when no models in active config")
        void notReady_whenNoModels() {
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("")));
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when no active config")
        void notReady_whenNoActiveConfig() {
            when(llmConfigCache.getActive()).thenReturn(Optional.empty());
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when chatModelFactory is null")
        void notReady_whenChatModelFactoryNull() {
            // isReady() short-circuits on null factory — stub is lenient because getActive() is never called
            lenient().when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("claude-3")));
            LangChainPromptSender sender = new LangChainPromptSender(null, llmConfigCache, skills);
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
        @DisplayName("throws LLMException when no models configured")
        void send_throwsLLMException_whenNotReady() {
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("")));
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("analyze this")))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException with no active config")
        void send_throwsLLMException_noActiveConfig() {
            when(llmConfigCache.getActive()).thenReturn(Optional.empty());
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
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
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("claude-3")));
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("test prompt")))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("works with skills=null (no NPE)")
        void send_nullSkills_wrapsException() {
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("claude-3")));
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, null);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("prompt")))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("request with systemPrompt and context sent without NPE")
        void send_withSystemPromptAndContext() {
            when(llmConfigCache.getActive()).thenReturn(Optional.of(activeConfig("claude-3")));
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("server error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
            LLMRequest request = LLMRequest.builder("analyze this")
                    .systemPrompt("You are a helpful assistant")
                    .context("some k8s context")
                    .build();

            assertThatThrownBy(() -> sender.send(request))
                    .isInstanceOf(LLMException.class);
        }
    }
}
