package com.causa.core.services.impl;

import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.api.validators.ConfigRequestValidator;
import com.causa.common.constants.ConfigConstants.LlmProvider;
import com.causa.common.exceptions.ConfigException;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.core.ports.LlmConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LlmConfigServiceImpl}.
 *
 * @since 0.0.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmConfigServiceImpl Tests")
class LlmConfigServiceImplTest {

    @Mock private LlmConfigRepository repository;
    @Mock private LlmConfigCache cache;
    @Mock private ConfigRequestValidator validator;

    private LlmConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmConfigServiceImpl(repository, cache, validator);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LlmConfig domainConfig(LlmProvider provider, boolean active) {
        return LlmConfig.builder()
            .id("llm_cnf_" + provider.name().toLowerCase())
            .provider(provider)
            .url("https://api.example.com")
            .models(List.of("model-1"))
            .temperature(BigDecimal.valueOf(0.1))
            .maxTokens(8192)
            .isActive(active)
            .authConfig(new AuthConfig("API_KEY", "********", null, null, null, null, null, null))
            .build();
    }

    private LlmConfigRequest buildRequest(String provider, boolean isActive) {
        LlmConfigRequest req = new LlmConfigRequest();
        req.setUrl("https://api.example.com");
        req.setModels(List.of("model-1"));
        req.setTemperature(BigDecimal.valueOf(0.1));
        req.setMaxTokens(8192);
        req.setIsActive(isActive);
        req.setAuthConfig(new AuthConfig("API_KEY", "sk-key", null, null, null, null, null, null));
        return req;
    }

    // =========================================================================
    // listAll()
    // =========================================================================

    @Nested
    @DisplayName("listAll()")
    class ListAllTests {

        @Test
        @DisplayName("returns all configs from cache with masked sensitive fields")
        void returnsFromCacheWithMasking() {
            LlmConfig anthropic = domainConfig(LlmProvider.ANTHROPIC, true);
            LlmConfig vertex = domainConfig(LlmProvider.VERTEX_AI, false);
            when(cache.getAll()).thenReturn(List.of(anthropic, vertex));

            List<LlmConfig> result = service.listAll();

            assertThat(result).hasSize(2);
            verify(cache).getAll();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("returns empty list when cache is empty")
        void returnsEmptyWhenCacheEmpty() {
            when(cache.getAll()).thenReturn(List.of());

            assertThat(service.listAll()).isEmpty();
        }

        @Test
        @DisplayName("masks apiKey in returned configs")
        void masksApiKey() {
            LlmConfig withRealKey = LlmConfig.builder()
                .id("llm_cnf_test")
                .provider(LlmProvider.ANTHROPIC)
                .url("https://api.anthropic.com")
                .models(List.of("claude-3"))
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", "real-api-key", null, null, null, null, null, null))
                .build();
            when(cache.getAll()).thenReturn(List.of(withRealKey));

            List<LlmConfig> result = service.listAll();

            assertThat(result.get(0).getAuthConfig().apiKey()).isEqualTo("********");
        }
    }

    // =========================================================================
    // getActive()
    // =========================================================================

    @Nested
    @DisplayName("getActive()")
    class GetActiveTests {

        @Test
        @DisplayName("returns masked active config from cache")
        void returnsMaskedActiveConfig() {
            LlmConfig active = domainConfig(LlmProvider.ANTHROPIC, true);
            when(cache.getActive()).thenReturn(Optional.of(active));

            Optional<LlmConfig> result = service.getActive();

            assertThat(result).isPresent();
            verify(cache).getActive();
        }

        @Test
        @DisplayName("returns empty when no active provider")
        void returnsEmptyWhenNoneActive() {
            when(cache.getActive()).thenReturn(Optional.empty());

            assertThat(service.getActive()).isEmpty();
        }
    }

    // =========================================================================
    // getByProvider()
    // =========================================================================

    @Nested
    @DisplayName("getByProvider()")
    class GetByProviderTests {

        @Test
        @DisplayName("returns masked config when found")
        void returnsMaskedConfig() {
            LlmConfig stored = LlmConfig.builder()
                .id("llm_cnf_test")
                .provider(LlmProvider.ANTHROPIC)
                .url("https://api.anthropic.com")
                .models(List.of("claude-3"))
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", "real-key", null, null, null, null, null, null))
                .build();
            when(repository.findByProvider("ANTHROPIC")).thenReturn(Optional.of(stored));

            LlmConfig result = service.getByProvider("ANTHROPIC");

            assertThat(result.getAuthConfig().apiKey()).isEqualTo("********");
        }

        @Test
        @DisplayName("throws ConfigException with NOT_FOUND when provider not found")
        void throwsWhenNotFound() {
            when(repository.findByProvider(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByProvider("OPENAI"))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("NOT_FOUND"));
        }

        @Test
        @DisplayName("normalises provider to upper case before repository call")
        void normalisesProviderToUpperCase() {
            when(repository.findByProvider("ANTHROPIC")).thenReturn(Optional.of(domainConfig(LlmProvider.ANTHROPIC, true)));

            service.getByProvider("anthropic");

            verify(repository).findByProvider("ANTHROPIC");
        }
    }

    // =========================================================================
    // upsert()
    // =========================================================================

    @Nested
    @DisplayName("upsert()")
    class UpsertTests {

        @Test
        @DisplayName("validates then persists then refreshes cache")
        void validatesPersistsRefreshes() {
            LlmConfigRequest req = buildRequest("ANTHROPIC", false);
            when(repository.save(any())).thenReturn(domainConfig(LlmProvider.ANTHROPIC, false));

            service.upsert("ANTHROPIC", req);

            verify(validator).validateLlm("API_KEY", req);
            verify(repository).save(any(LlmConfig.class));
            verify(cache).refresh();
        }

        @Test
        @DisplayName("throws UNKNOWN_PROVIDER for an unregistered provider string")
        void throwsForUnknownProvider() {
            LlmConfigRequest req = buildRequest("UNKNOWN", false);

            assertThatThrownBy(() -> service.upsert("UNKNOWN_PROV", req))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("UNKNOWN_PROVIDER"));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("throws VALIDATION_ERROR when auth_config has no authType")
        void throwsWhenAuthTypeNull() {
            LlmConfigRequest req = buildRequest("ANTHROPIC", false);
            req.setAuthConfig(new AuthConfig(null, "key", null, null, null, null, null, null));

            assertThatThrownBy(() -> service.upsert("ANTHROPIC", req))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("deactivates all other providers when isActive=true")
        void deactivatesAllWhenSettingActive() {
            LlmConfigRequest req = buildRequest("ANTHROPIC", true);
            when(repository.save(any())).thenReturn(domainConfig(LlmProvider.ANTHROPIC, true));

            service.upsert("ANTHROPIC", req);

            verify(repository).deactivateAll();
        }

        @Test
        @DisplayName("does not call deactivateAll when isActive=false")
        void doesNotDeactivateWhenNotActive() {
            LlmConfigRequest req = buildRequest("ANTHROPIC", false);
            when(repository.save(any())).thenReturn(domainConfig(LlmProvider.ANTHROPIC, false));

            service.upsert("ANTHROPIC", req);

            verify(repository, never()).deactivateAll();
        }

        @Test
        @DisplayName("returned config has sensitive fields masked")
        void returnsMaskedConfig() {
            LlmConfigRequest req = buildRequest("ANTHROPIC", true);
            LlmConfig savedWithEncryptedKey = LlmConfig.builder()
                .id("llm_cnf_test")
                .provider(LlmProvider.ANTHROPIC)
                .url("https://api.anthropic.com")
                .models(List.of("claude-3"))
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", "ENCRYPTED_KEY", null, null, null, null, null, null))
                .build();
            when(repository.save(any())).thenReturn(savedWithEncryptedKey);

            LlmConfig result = service.upsert("ANTHROPIC", req);

            assertThat(result.getAuthConfig().apiKey()).isEqualTo("********");
        }

        @Test
        @DisplayName("provider string is case-insensitive")
        void providerCaseInsensitive() {
            LlmConfigRequest req = buildRequest("anthropic", false);
            when(repository.save(any())).thenReturn(domainConfig(LlmProvider.ANTHROPIC, false));

            assertThatNoException().isThrownBy(() -> service.upsert("anthropic", req));
        }
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("deletes inactive provider and refreshes cache")
        void deletesInactiveAndRefreshesCache() {
            when(repository.findByProvider("ANTHROPIC"))
                .thenReturn(Optional.of(domainConfig(LlmProvider.ANTHROPIC, false)));

            service.delete("ANTHROPIC");

            verify(repository).deleteByProvider("ANTHROPIC");
            verify(cache).refresh();
        }

        @Test
        @DisplayName("throws ConfigException with NOT_FOUND when provider not found")
        void throwsWhenNotFound() {
            when(repository.findByProvider("OPENAI")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete("OPENAI"))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("NOT_FOUND"));

            verify(repository, never()).deleteByProvider(any());
            verify(cache, never()).refresh();
        }

        @Test
        @DisplayName("throws ConfigException with CONFLICT when deleting active provider")
        void throwsWhenDeletingActiveProvider() {
            when(repository.findByProvider("ANTHROPIC"))
                .thenReturn(Optional.of(domainConfig(LlmProvider.ANTHROPIC, true)));

            assertThatThrownBy(() -> service.delete("ANTHROPIC"))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("CONFLICT"))
                .hasMessageContaining("active");

            verify(repository, never()).deleteByProvider(any());
            verify(cache, never()).refresh();
        }

        @Test
        @DisplayName("normalises provider to upper case before repository call")
        void normalisesProviderToUpperCase() {
            when(repository.findByProvider("ANTHROPIC"))
                .thenReturn(Optional.of(domainConfig(LlmProvider.ANTHROPIC, false)));

            service.delete("anthropic");

            verify(repository).deleteByProvider("ANTHROPIC");
        }
    }
}
