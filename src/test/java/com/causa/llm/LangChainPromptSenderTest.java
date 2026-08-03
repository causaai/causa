package com.causa.llm;

import com.causa.common.exceptions.LLMException;
import com.causa.config.AppConfig;
import com.causa.config.LlmConfigSnapshot;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LangChainPromptSender Tests")
class LangChainPromptSenderTest {

    @Mock ChatModelFactory chatModelFactory;
    @Mock ChatModel chatModel;
    @Mock AppConfig appConfig;
    @Mock LlmConfigSnapshot llmConfig;
    @Mock Skills skills;

    // -----------------------------------------------------------------------
    // isReady()
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("isReady() Tests")
    class IsReadyTests {

        @Test
        @DisplayName("returns true when chatModelFactory non-null and modelName present")
        void ready_whenChatModelFactoryAndModelNamePresent() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("claude-3");
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
            assertThat(sender.isReady()).isTrue();
        }

        @Test
        @DisplayName("returns false when modelName is empty")
        void notReady_whenModelNameEmpty() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("");
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when modelName is blank")
        void notReady_whenModelNameBlank() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("   ");
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
            assertThat(sender.isReady()).isFalse();
        }

        @Test
        @DisplayName("returns false when chatModelFactory is null")
        void notReady_whenChatModelFactoryNull() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("claude-3");
            LangChainPromptSender sender = new LangChainPromptSender(null, appConfig, skills);
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
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("");
            when(llmConfig.getProvider()).thenReturn("vertex-ai-anthropic");
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);

            LLMRequest request = LLMRequest.of("analyze this");
            assertThatThrownBy(() -> sender.send(request))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException with no provider configured")
        void send_throwsLLMException_noProvider() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("");
            when(llmConfig.getProvider()).thenReturn("");
            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);

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
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("claude-3");
            when(llmConfig.getProvider()).thenReturn("anthropic");
            when(llmConfig.isSkillsEnabled()).thenReturn(false);
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("test prompt")))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("works with skills=null (no NPE)")
        void send_nullSkills_wrapsException() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("claude-3");
            when(llmConfig.getProvider()).thenReturn("anthropic");
            when(llmConfig.isSkillsEnabled()).thenReturn(false);
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, null);
            assertThatThrownBy(() -> sender.send(LLMRequest.of("prompt")))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("request with systemPrompt and context sent without NPE")
        void send_withSystemPromptAndContext() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getModelName()).thenReturn("claude-3");
            when(llmConfig.getProvider()).thenReturn("anthropic");
            when(llmConfig.isSkillsEnabled()).thenReturn(false);
            when(chatModelFactory.chatModel()).thenReturn(chatModel);
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("server error"));

            LangChainPromptSender sender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
            LLMRequest request = LLMRequest.builder("analyze this")
                    .systemPrompt("You are a helpful assistant")
                    .context("some k8s context")
                    .build();

            assertThatThrownBy(() -> sender.send(request))
                    .isInstanceOf(LLMException.class);
        }
    }
}
