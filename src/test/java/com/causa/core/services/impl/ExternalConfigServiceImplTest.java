package com.causa.core.services.impl;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.validators.ConfigRequestValidator;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.exceptions.ConfigException;
import com.causa.config.ExternalConfigCache;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.ports.ExternalConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExternalConfigServiceImpl}.
 *
 * @since 0.0.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalConfigServiceImpl Tests")
class ExternalConfigServiceImplTest {

    @Mock private ExternalConfigRepository repository;
    @Mock private ExternalConfigCache cache;
    @Mock private ConfigRequestValidator validator;

    private ExternalConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExternalConfigServiceImpl(repository, cache, validator);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ExternalConfig domainConfig(PlatformCategory category, String platform, String name) {
        return ExternalConfig.builder()
            .id("ext_cnf_test")
            .category(category)
            .platform(platform)
            .name(name)
            .url("https://example.com")
            .isActive(true)
            .authConfig(new AuthConfig("API_KEY", "********", null, null, null, null, null, null))
            .build();
    }

    private ExternalConfigRequest buildRequest(String name, String url, AuthConfig auth) {
        ExternalConfigRequest req = new ExternalConfigRequest();
        req.setName(name);
        req.setUrl(url);
        req.setIsActive(true);
        req.setAuthConfig(auth);
        return req;
    }

    // =========================================================================
    // listByCategory()
    // =========================================================================

    @Nested
    @DisplayName("listByCategory()")
    class ListByCategoryTests {

        @Test
        @DisplayName("returns masked configs from cache for OBSERVABILITY")
        void returnsObservabilityFromCache() {
            ExternalConfig obs = domainConfig(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            when(cache.getByCategory(PlatformCategory.OBSERVABILITY)).thenReturn(List.of(obs));

            List<ExternalConfig> result = service.listByCategory(PlatformCategory.OBSERVABILITY);

            assertThat(result).hasSize(1);
            verify(cache).getByCategory(PlatformCategory.OBSERVABILITY);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("returns empty list when cache has no configs for category")
        void returnsEmptyWhenNoneInCache() {
            when(cache.getByCategory(any())).thenReturn(List.of());

            assertThat(service.listByCategory(PlatformCategory.INTEGRATION)).isEmpty();
        }

        @Test
        @DisplayName("sensitive auth fields are masked in returned configs")
        void masksApiKeyInResponse() {
            ExternalConfig withToken = ExternalConfig.builder()
                .id("ext_cnf_test")
                .category(PlatformCategory.OBSERVABILITY)
                .platform("DATADOG")
                .name("dd-prod")
                .url("https://api.datadoghq.com")
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", "real-api-key", "real-app-key", null, null, null, null, null))
                .build();
            when(cache.getByCategory(PlatformCategory.OBSERVABILITY)).thenReturn(List.of(withToken));

            List<ExternalConfig> result = service.listByCategory(PlatformCategory.OBSERVABILITY);

            assertThat(result.get(0).getAuthConfig().apiKey()).isEqualTo("********");
            assertThat(result.get(0).getAuthConfig().appKey()).isEqualTo("********");
        }
    }

    // =========================================================================
    // getByPlatformAndName()
    // =========================================================================

    @Nested
    @DisplayName("getByPlatformAndName()")
    class GetByPlatformAndNameTests {

        @Test
        @DisplayName("returns masked config from repository when found")
        void returnsMaskedConfig() {
            ExternalConfig stored = ExternalConfig.builder()
                .id("ext_cnf_test")
                .category(PlatformCategory.OBSERVABILITY)
                .platform("INSTANA")
                .name("instana-prod")
                .url("https://example.com")
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", null, null, "real-token", null, null, null, null))
                .build();
            when(repository.findByPlatformAndName("INSTANA", "instana-prod"))
                .thenReturn(Optional.of(stored));

            ExternalConfig result = service.getByPlatformAndName("INSTANA", "instana-prod");

            assertThat(result.getAuthConfig().token()).isEqualTo("********");
        }

        @Test
        @DisplayName("throws ConfigException when config not found")
        void throwsWhenNotFound() {
            when(repository.findByPlatformAndName(anyString(), anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByPlatformAndName("DATADOG", "missing"))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("NOT_FOUND"));
        }

        @Test
        @DisplayName("normalises platform to upper case before repository call")
        void normalisesPlatformToUpperCase() {
            ExternalConfig stored = domainConfig(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            when(repository.findByPlatformAndName("DATADOG", "dd-prod")).thenReturn(Optional.of(stored));

            service.getByPlatformAndName("datadog", "dd-prod");

            verify(repository).findByPlatformAndName("DATADOG", "dd-prod");
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
            AuthConfig auth = new AuthConfig("API_KEY", "dd-api-key", "dd-app-key", null, null, null, null, null);
            ExternalConfigRequest req = buildRequest("dd-prod", "https://api.datadoghq.com", auth);
            ExternalConfig saved = domainConfig(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            when(repository.save(any())).thenReturn(saved);

            service.upsert(PlatformCategory.OBSERVABILITY, "DATADOG", req);

            verify(validator).validateExternal("DATADOG", req);
            verify(repository).save(any(ExternalConfig.class));
            verify(cache).refresh();
        }

        @Test
        @DisplayName("passes platform uppercased to validator")
        void passesUppercasedPlatformToValidator() {
            AuthConfig auth = new AuthConfig("API_KEY", "key", "appkey", null, null, null, null, null);
            ExternalConfigRequest req = buildRequest("dd-prod", "https://api.datadoghq.com", auth);
            when(repository.save(any())).thenReturn(domainConfig(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod"));

            service.upsert(PlatformCategory.OBSERVABILITY, "datadog", req);

            verify(validator).validateExternal("DATADOG", req);
        }

        @Test
        @DisplayName("throws ConfigException when validation fails")
        void throwsOnValidationFailure() {
            ExternalConfigRequest req = buildRequest(null, null, null);
            doThrow(new ConfigException("name is required", "VALIDATION_ERROR"))
                .when(validator).validateExternal(anyString(), any());

            assertThatThrownBy(() -> service.upsert(PlatformCategory.OBSERVABILITY, "DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("name is required");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("returned config has sensitive fields masked")
        void returnsMaskedConfig() {
            AuthConfig authWithRealKey = new AuthConfig("API_KEY", "dd-api-key", "dd-app-key", null, null, null, null, null);
            ExternalConfigRequest req = buildRequest("dd-prod", "https://api.datadoghq.com", authWithRealKey);
            ExternalConfig savedWithEncryptedKey = ExternalConfig.builder()
                .id("ext_cnf_test")
                .category(PlatformCategory.OBSERVABILITY)
                .platform("DATADOG")
                .name("dd-prod")
                .url("https://api.datadoghq.com")
                .isActive(true)
                .authConfig(new AuthConfig("API_KEY", "ENCRYPTED_KEY", "ENCRYPTED_APP_KEY", null, null, null, null, null))
                .build();
            when(repository.save(any())).thenReturn(savedWithEncryptedKey);

            ExternalConfig result = service.upsert(PlatformCategory.OBSERVABILITY, "DATADOG", req);

            assertThat(result.getAuthConfig().apiKey()).isEqualTo("********");
            assertThat(result.getAuthConfig().appKey()).isEqualTo("********");
        }

        @Test
        @DisplayName("non-sensitive fields (name, url, isActive) pass through without masking")
        void nonSensitiveFieldsPassThrough() {
            AuthConfig auth = new AuthConfig("WEBHOOK", null, null, "token", null, null, null, null);
            ExternalConfigRequest req = buildRequest("slack-alerts", "https://hooks.slack.com/...", auth);
            req.setAdditionalConfig(Map.of("channel", "#alerts"));
            ExternalConfig saved = ExternalConfig.builder()
                .id("ext_cnf_test")
                .category(PlatformCategory.INTEGRATION)
                .platform("SLACK")
                .name("slack-alerts")
                .url("https://hooks.slack.com/...")
                .isActive(true)
                .authConfig(new AuthConfig("WEBHOOK", null, null, "encrypted-token", null, null, null, null))
                .additionalConfig(Map.of("channel", "#alerts"))
                .build();
            when(repository.save(any())).thenReturn(saved);

            ExternalConfig result = service.upsert(PlatformCategory.INTEGRATION, "SLACK", req);

            assertThat(result.getName()).isEqualTo("slack-alerts");
            assertThat(result.getUrl()).isEqualTo("https://hooks.slack.com/...");
            assertThat(result.isActive()).isTrue();
        }
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("deletes config and refreshes cache on success")
        void deletesAndRefreshesCache() {
            when(repository.deleteByPlatformAndName("DATADOG", "dd-prod")).thenReturn(true);

            service.delete("DATADOG", "dd-prod");

            verify(repository).deleteByPlatformAndName("DATADOG", "dd-prod");
            verify(cache).refresh();
        }

        @Test
        @DisplayName("throws ConfigException when config not found")
        void throwsWhenNotFound() {
            when(repository.deleteByPlatformAndName(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.delete("DATADOG", "missing"))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("NOT_FOUND"));

            verify(cache, never()).refresh();
        }

        @Test
        @DisplayName("normalises platform to upper case before repository call")
        void normalisesPlatformToUpperCase() {
            when(repository.deleteByPlatformAndName("SLACK", "my-slack")).thenReturn(true);

            service.delete("slack", "my-slack");

            verify(repository).deleteByPlatformAndName("SLACK", "my-slack");
        }
    }
}
