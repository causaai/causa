package com.causa.config;

import com.causa.common.constants.ConfigConstants.LlmProvider;
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

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LlmConfigCache}.
 *
 * @since 0.0.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmConfigCache Tests")
class LlmConfigCacheTest {

    @Mock
    private LlmConfigRepository repository;

    private LlmConfigCache cache;

    @BeforeEach
    void setUp() {
        cache = new LlmConfigCache(repository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LlmConfig config(LlmProvider provider, boolean active) {
        return LlmConfig.builder()
            .id("llm_cnf_" + provider.name().toLowerCase())
            .provider(provider)
            .url("https://api.example.com")
            .models(List.of("model-1"))
            .authConfig(new AuthConfig("API_KEY", "key", null, null, null, null, null, null))
            .isActive(active)
            .build();
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Initial state (before first refresh)")
    class InitialStateTests {

        @Test
        @DisplayName("getAll() returns empty list before refresh")
        void getAll_emptyBeforeRefresh() {
            assertThat(cache.getAll()).isEmpty();
        }

        @Test
        @DisplayName("getActive() returns empty before refresh")
        void getActive_emptyBeforeRefresh() {
            assertThat(cache.getActive()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("loads all configs from repository after refresh")
        void loadsAllConfigs() {
            LlmConfig a = config(LlmProvider.ANTHROPIC, true);
            LlmConfig v = config(LlmProvider.VERTEX_AI, false);
            when(repository.findAll()).thenReturn(List.of(a, v));

            cache.refresh();

            assertThat(cache.getAll()).hasSize(2).containsExactlyInAnyOrder(a, v);
        }

        @Test
        @DisplayName("sets active to the single active provider")
        void setsActiveProvider() {
            LlmConfig active = config(LlmProvider.ANTHROPIC, true);
            LlmConfig inactive = config(LlmProvider.VERTEX_AI, false);
            when(repository.findAll()).thenReturn(List.of(active, inactive));

            cache.refresh();

            assertThat(cache.getActive()).contains(active);
        }

        @Test
        @DisplayName("getActive() returns empty when no provider is active")
        void getActive_emptyWhenNoneActive() {
            when(repository.findAll()).thenReturn(List.of(
                config(LlmProvider.ANTHROPIC, false),
                config(LlmProvider.VERTEX_AI, false)
            ));

            cache.refresh();

            assertThat(cache.getActive()).isEmpty();
        }

        @Test
        @DisplayName("getAll() returns empty list when repository returns empty")
        void getAll_emptyRepositoryResult() {
            when(repository.findAll()).thenReturn(List.of());

            cache.refresh();

            assertThat(cache.getAll()).isEmpty();
            assertThat(cache.getActive()).isEmpty();
        }

        @Test
        @DisplayName("preserves stale cache on repository exception")
        void preservesStaleCacheOnException() {
            LlmConfig stale = config(LlmProvider.ANTHROPIC, true);
            when(repository.findAll())
                .thenReturn(List.of(stale))
                .thenThrow(new RuntimeException("DB down"));

            cache.refresh(); // first: succeeds, stale loaded
            cache.refresh(); // second: throws, stale preserved

            assertThat(cache.getAll()).containsExactly(stale);
            assertThat(cache.getActive()).contains(stale);
        }

        @Test
        @DisplayName("second refresh replaces the first snapshot")
        void secondRefreshReplacesSnapshot() {
            LlmConfig first = config(LlmProvider.ANTHROPIC, true);
            LlmConfig second = config(LlmProvider.VERTEX_AI, true);
            when(repository.findAll())
                .thenReturn(List.of(first))
                .thenReturn(List.of(second));

            cache.refresh();
            cache.refresh();

            assertThat(cache.getAll()).containsExactly(second);
            assertThat(cache.getActive()).contains(second);
        }
    }

    // -------------------------------------------------------------------------
    // Thread-safety smoke test
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent read during refresh")
    class ConcurrencyTests {

        @Test
        @DisplayName("getAll() never returns null during concurrent refresh")
        void getAll_neverNull() throws InterruptedException {
            when(repository.findAll()).thenReturn(List.of(config(LlmProvider.ANTHROPIC, true)));

            Thread writer = new Thread(() -> {
                for (int i = 0; i < 50; i++) cache.refresh();
            });
            writer.start();
            for (int i = 0; i < 200; i++) {
                assertThat(cache.getAll()).isNotNull();
            }
            writer.join();
        }
    }
}
