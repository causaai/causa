package com.causa.infrastructure.health;

import com.causa.infrastructure.persistence.DatabaseConnectionService;
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
@DisplayName("DbHealthCheck Tests")
class DbHealthCheckTest {

    @Mock DatabaseConnectionService databaseConnectionService;
    @InjectMocks DbHealthCheck dbHealthCheck;

    @Nested @DisplayName("call() when DB is ready")
    class WhenReady {
        @Test void returnsUp() {
            when(databaseConnectionService.isReady()).thenReturn(true);
            HealthCheckResponse r = dbHealthCheck.call();
            assertThat(r.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        }
        @Test void hasName() {
            when(databaseConnectionService.isReady()).thenReturn(true);
            assertThat(dbHealthCheck.call().getName()).isNotBlank();
        }
    }

    @Nested @DisplayName("call() when DB is not ready")
    class WhenNotReady {
        @Test void returnsDown() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            HealthCheckResponse r = dbHealthCheck.call();
            assertThat(r.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        }
    }
}
