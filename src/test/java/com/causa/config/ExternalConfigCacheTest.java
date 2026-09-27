package com.causa.config;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExternalConfigCache}.
 *
 * @since 0.0.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalConfigCache Tests")
class ExternalConfigCacheTest {

    @Mock
    private ExternalConfigRepository repository;

    private ExternalConfigCache cache;

    @BeforeEach
    void setUp() {
        cache = new ExternalConfigCache(repository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ExternalConfig config(PlatformCategory category, String platform, String name) {
        return ExternalConfig.builder()
            .id("ext_cnf_" + name)
            .category(category)
            .platform(platform)
            .name(name)
            .url("https://example.com")
            .isActive(true)
            .authConfig(new AuthConfig("API_KEY", "key", null, null, null, null, null, null))
            .build();
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Initial state (before first refresh)")
    class InitialStateTests {

        @Test
        @DisplayName("getByCategory(OBSERVABILITY) returns empty list before refresh")
        void observability_emptyBeforeRefresh() {
            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY)).isEmpty();
        }

        @Test
        @DisplayName("getByCategory(INTEGRATION) returns empty list before refresh")
        void integration_emptyBeforeRefresh() {
            assertThat(cache.getByCategory(PlatformCategory.INTEGRATION)).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("loads observability configs by category after refresh")
        void loadsObservabilityConfigs() {
            ExternalConfig dd = config(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            ExternalConfig instana = config(PlatformCategory.OBSERVABILITY, "INSTANA", "instana-prod");
            when(repository.findByCategory(PlatformCategory.OBSERVABILITY)).thenReturn(List.of(dd, instana));
            when(repository.findByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of());

            cache.refresh();

            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY))
                .hasSize(2)
                .containsExactlyInAnyOrder(dd, instana);
        }

        @Test
        @DisplayName("loads integration configs by category after refresh")
        void loadsIntegrationConfigs() {
            ExternalConfig slack = config(PlatformCategory.INTEGRATION, "SLACK", "slack-alerts");
            when(repository.findByCategory(PlatformCategory.OBSERVABILITY)).thenReturn(List.of());
            when(repository.findByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of(slack));

            cache.refresh();

            assertThat(cache.getByCategory(PlatformCategory.INTEGRATION))
                .containsExactly(slack);
        }

        @Test
        @DisplayName("both categories populated independently")
        void bothCategoriesPopulatedIndependently() {
            ExternalConfig obs = config(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            ExternalConfig intg = config(PlatformCategory.INTEGRATION, "SLACK", "slack-alerts");
            when(repository.findByCategory(PlatformCategory.OBSERVABILITY)).thenReturn(List.of(obs));
            when(repository.findByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of(intg));

            cache.refresh();

            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY)).containsExactly(obs);
            assertThat(cache.getByCategory(PlatformCategory.INTEGRATION)).containsExactly(intg);
        }

        @Test
        @DisplayName("returns empty list when no configs exist for a category")
        void emptyWhenNoneExist() {
            when(repository.findByCategory(any())).thenReturn(List.of());

            cache.refresh();

            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY)).isEmpty();
            assertThat(cache.getByCategory(PlatformCategory.INTEGRATION)).isEmpty();
        }

        @Test
        @DisplayName("preserves stale cache on repository exception")
        void preservesStaleCacheOnException() {
            ExternalConfig stale = config(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-prod");
            when(repository.findByCategory(PlatformCategory.OBSERVABILITY))
                .thenReturn(List.of(stale))
                .thenThrow(new RuntimeException("DB down"));
            when(repository.findByCategory(PlatformCategory.INTEGRATION))
                .thenReturn(List.of());

            cache.refresh(); // succeeds — stale loaded
            cache.refresh(); // throws on observability — stale preserved

            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY)).containsExactly(stale);
        }

        @Test
        @DisplayName("second refresh replaces previous snapshot for a category")
        void secondRefreshReplacesSnapshot() {
            ExternalConfig first = config(PlatformCategory.OBSERVABILITY, "DATADOG", "dd-v1");
            ExternalConfig second = config(PlatformCategory.OBSERVABILITY, "INSTANA", "instana-v2");
            when(repository.findByCategory(PlatformCategory.OBSERVABILITY))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second));
            when(repository.findByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of());

            cache.refresh();
            cache.refresh();

            assertThat(cache.getByCategory(PlatformCategory.OBSERVABILITY)).containsExactly(second);
        }

        @Test
        @DisplayName("queries all PlatformCategory values on each refresh")
        void queriesAllCategories() {
            when(repository.findByCategory(any())).thenReturn(List.of());

            cache.refresh();

            // One call per PlatformCategory enum value (OBSERVABILITY + INTEGRATION = 2)
            verify(repository, times(PlatformCategory.values().length)).findByCategory(any());
        }
    }
}
