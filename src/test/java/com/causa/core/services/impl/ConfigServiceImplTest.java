package com.causa.core.services.impl;

import com.causa.config.AppConfig;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.services.ConfigService;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfigServiceImpl}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigServiceImpl Tests")
class ConfigServiceImplTest {

    @Mock
    private AppConfig appConfig;

    @Mock
    private ConfigurationRepository repository;

    @Mock
    private Config mpConfig;

    private ConfigServiceImpl configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigServiceImpl(appConfig, repository, mpConfig);
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("get() Tests")
    class GetTests {

        @Test
        @DisplayName("Should delegate to appConfig")
        void shouldDelegateToAppConfig() {
            when(appConfig.get("LLM_PROVIDER")).thenReturn(Optional.of("anthropic"));

            Optional<String> result = configService.get("LLM_PROVIDER");

            assertTrue(result.isPresent());
            assertEquals("anthropic", result.get());
            verify(appConfig).get("LLM_PROVIDER");
        }

        @Test
        @DisplayName("Should return empty when key not in cache")
        void shouldReturnEmptyWhenKeyNotInCache() {
            when(appConfig.get("LLM_PROVIDER")).thenReturn(Optional.empty());

            Optional<String> result = configService.get("LLM_PROVIDER");

            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // getByCategory
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getByCategory() Tests")
    class GetByCategoryTests {

        @Test
        @DisplayName("Should return only keys in the requested category")
        void shouldReturnKeysInCategory() {
            when(appConfig.get(anyString())).thenReturn(Optional.of("some-value"));

            List<ConfigurationRepository.ConfigEntry> result = configService.getByCategory("cluster");

            assertFalse(result.isEmpty());
            result.forEach(e -> assertTrue(
                    e.key().equals("CLUSTER_NAME") || e.key().equals("CLUSTER_TYPE"),
                    "Unexpected key in cluster category: " + e.key()));
        }

        @Test
        @DisplayName("Should return LLM keys for llm category")
        void shouldReturnLlmKeys() {
            when(appConfig.get(anyString())).thenReturn(Optional.empty());

            List<ConfigurationRepository.ConfigEntry> result = configService.getByCategory("llm");

            assertFalse(result.isEmpty());
            result.forEach(e -> assertFalse(
                    e.key().equals("CLUSTER_NAME") || e.key().startsWith("ALERT_"),
                    "Unexpected non-LLM key: " + e.key()));
        }

        @Test
        @DisplayName("Should mask sensitive keys in category listing")
        void shouldMaskSensitiveKeysInCategoryListing() {
            when(appConfig.get("LLM_API_KEY")).thenReturn(Optional.of("my-secret"));
            when(appConfig.get(argThat(k -> !"LLM_API_KEY".equals(k)))).thenReturn(Optional.empty());

            List<ConfigurationRepository.ConfigEntry> result = configService.getByCategory("llm");

            ConfigurationRepository.ConfigEntry apiKeyEntry = result.stream()
                    .filter(e -> e.key().equals("LLM_API_KEY"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(apiKeyEntry.encrypted());
        }
    }

    // -------------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should return entries for all known keys")
        void shouldReturnEntriesForAllKnownKeys() {
            when(appConfig.get(anyString())).thenReturn(Optional.of("value"));

            List<ConfigurationRepository.ConfigEntry> result = configService.getAll();

            assertFalse(result.isEmpty());
            // All known keys (22) should be present
            assertEquals(22, result.size());
        }

        @Test
        @DisplayName("Should return empty value when key not set")
        void shouldReturnEmptyValueWhenKeyNotSet() {
            when(appConfig.get(anyString())).thenReturn(Optional.empty());

            List<ConfigurationRepository.ConfigEntry> result = configService.getAll();

            result.forEach(e -> assertEquals("", e.value(), "Expected empty string for unset key: " + e.key()));
        }
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should persist to repository and update cache for non-sensitive key")
        void shouldPersistAndCacheNonSensitiveKey() {
            configService.update("LLM_PROVIDER", "ollama");

            verify(repository).upsert(eq("LLM_PROVIDER"), eq("ollama"), eq(false));
            verify(appConfig).put("LLM_PROVIDER", "ollama");
        }

        @Test
        @DisplayName("Should encrypt and persist sensitive key")
        void shouldEncryptAndPersistSensitiveKey() {
            configService.update("LLM_API_KEY", "plaintext-secret");

            // Should store encrypted value in DB (not the plaintext)
            verify(repository).upsert(eq("LLM_API_KEY"), argThat(v -> !"plaintext-secret".equals(v)), eq(true));
            // Cache should hold the plaintext value for fast access
            verify(appConfig).put("LLM_API_KEY", "plaintext-secret");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null key")
        void shouldThrowForNullKey() {
            assertThrows(IllegalArgumentException.class, () -> configService.update(null, "value"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank key")
        void shouldThrowForBlankKey() {
            assertThrows(IllegalArgumentException.class, () -> configService.update("  ", "value"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null value")
        void shouldThrowForNullValue() {
            assertThrows(IllegalArgumentException.class, () -> configService.update("LLM_PROVIDER", null));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank value")
        void shouldThrowForBlankValue() {
            assertThrows(IllegalArgumentException.class, () -> configService.update("LLM_PROVIDER", "  "));
        }
    }

    // -------------------------------------------------------------------------
    // loadFromDbAndEnv / refreshCache
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("loadFromDbAndEnv() Tests")
    class LoadFromDbAndEnvTests {

        @Test
        @DisplayName("Should load entries from DB into cache")
        void shouldLoadEntriesFromDb() {
            List<ConfigurationRepository.ConfigEntry> dbEntries = List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "anthropic", false),
                    new ConfigurationRepository.ConfigEntry("CLUSTER_NAME", "prod-cluster", false)
            );
            when(repository.findAll()).thenReturn(dbEntries);
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            configService.loadFromDbAndEnv();

            verify(repository).findAll();
            verify(appConfig).clear();
            verify(appConfig).put("LLM_PROVIDER", "anthropic");
            verify(appConfig).put("CLUSTER_NAME", "prod-cluster");
        }

        @Test
        @DisplayName("Should seed missing keys from MicroProfile Config")
        void shouldSeedMissingKeysFromMpConfig() {
            when(repository.findAll()).thenReturn(List.of());
            when(appConfig.get(anyString())).thenReturn(Optional.empty());
            // Seed LLM_PROVIDER from env
            when(mpConfig.getOptionalValue("causa.llm.provider", String.class))
                    .thenReturn(Optional.of("ollama"));
            when(mpConfig.getOptionalValue(argThat(k -> !"causa.llm.provider".equals(k)), eq(String.class)))
                    .thenReturn(Optional.empty());

            configService.loadFromDbAndEnv();

            // Should upsert the seeded key to DB
            verify(repository).upsert(eq("LLM_PROVIDER"), eq("ollama"), eq(false));
            verify(appConfig).put("LLM_PROVIDER", "ollama");
        }

        @Test
        @DisplayName("Should handle empty DB gracefully")
        void shouldHandleEmptyDb() {
            when(repository.findAll()).thenReturn(List.of());
            when(appConfig.get(anyString())).thenReturn(Optional.empty());
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> configService.loadFromDbAndEnv());
            verify(appConfig).clear();
        }
    }

    @Nested
    @DisplayName("refreshCache() Tests")
    class RefreshCacheTests {

        @Test
        @DisplayName("Should return CacheRefreshResult")
        void shouldReturnCacheRefreshResult() {
            when(repository.findAll()).thenReturn(List.of());
            when(appConfig.get(anyString())).thenReturn(Optional.empty());
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            ConfigService.CacheRefreshResult result = configService.refreshCache();

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should refresh cache from DB")
        void shouldRefreshCacheFromDb() {
            when(repository.findAll()).thenReturn(List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "anthropic", false)
            ));
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            configService.refreshCache();

            verify(repository).findAll();
            verify(appConfig).put("LLM_PROVIDER", "anthropic");
        }
    }
}
