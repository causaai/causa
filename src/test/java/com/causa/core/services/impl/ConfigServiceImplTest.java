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
        @DisplayName("Should return alerts keys for alerts category")
        void shouldReturnAlertsKeys() {
            when(appConfig.get(anyString())).thenReturn(Optional.empty());

            List<ConfigurationRepository.ConfigEntry> result = configService.getByCategory("alerts");

            assertFalse(result.isEmpty());
            result.forEach(e -> assertTrue(
                    e.key().startsWith("ALERT_"),
                    "Unexpected non-alerts key: " + e.key()));
        }

        @Test
        @DisplayName("Should return no entries for unknown category")
        void shouldReturnEmptyForUnknownCategory() {
            List<ConfigurationRepository.ConfigEntry> result = configService.getByCategory("llm");

            assertTrue(result.isEmpty(),
                    "LLM keys have been removed from generic_configs — expected empty list");
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
            // 4 alerts keys + 2 cluster keys = 6 total (LLM keys moved to llm_configs table)
            assertEquals(6, result.size());
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
            configService.update("CLUSTER_NAME", "prod-cluster");

            verify(repository).upsert(eq("CLUSTER_NAME"), eq("prod-cluster"), eq(false));
            verify(appConfig).put("CLUSTER_NAME", "prod-cluster");
        }

        @Test
        @DisplayName("Should persist alert cooldown key without encryption")
        void shouldPersistAlertCooldownKey() {
            configService.update("ALERT_COOLDOWN_MINUTES", "15");

            verify(repository).upsert(eq("ALERT_COOLDOWN_MINUTES"), eq("15"), eq(false));
            verify(appConfig).put("ALERT_COOLDOWN_MINUTES", "15");
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
                    new ConfigurationRepository.ConfigEntry("ALERT_FILTER_SEVERITY", "critical", false),
                    new ConfigurationRepository.ConfigEntry("CLUSTER_NAME", "prod-cluster", false)
            );
            when(repository.findAll()).thenReturn(dbEntries);
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            configService.loadFromDbAndEnv();

            verify(repository).findAll();
            verify(appConfig).clear();
            verify(appConfig).put("ALERT_FILTER_SEVERITY", "critical");
            verify(appConfig).put("CLUSTER_NAME", "prod-cluster");
        }

        @Test
        @DisplayName("Should seed missing keys from MicroProfile Config")
        void shouldSeedMissingKeysFromMpConfig() {
            when(repository.findAll()).thenReturn(List.of());
            when(appConfig.get(anyString())).thenReturn(Optional.empty());
            // Seed CLUSTER_NAME from env
            when(mpConfig.getOptionalValue("causa.cluster.name", String.class))
                    .thenReturn(Optional.of("my-cluster"));
            when(mpConfig.getOptionalValue(argThat(k -> !"causa.cluster.name".equals(k)), eq(String.class)))
                    .thenReturn(Optional.empty());

            configService.loadFromDbAndEnv();

            // Should upsert the seeded key to DB
            verify(repository).upsert(eq("CLUSTER_NAME"), eq("my-cluster"), eq(false));
            verify(appConfig).put("CLUSTER_NAME", "my-cluster");
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
                    new ConfigurationRepository.ConfigEntry("CLUSTER_NAME", "prod-cluster", false)
            ));
            when(mpConfig.getOptionalValue(anyString(), eq(String.class))).thenReturn(Optional.empty());

            configService.refreshCache();

            verify(repository).findAll();
            verify(appConfig).put("CLUSTER_NAME", "prod-cluster");
        }
    }
}
