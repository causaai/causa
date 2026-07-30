package com.causa.infrastructure.persistence;

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
@DisplayName("DatabaseConnectionService Tests")
class DatabaseConnectionServiceTest {

    @Mock javax.sql.DataSource dataSource;
    @InjectMocks DatabaseConnectionService service;

    @Nested @DisplayName("isReady() / setReady() Tests")
    class ReadinessTests {
        @Test void defaultIsFalse() {
            assertThat(service.isReady()).isFalse();
        }
        @Test void setReadyTrue_isReadyReturnsTrue() {
            service.setReady(true);
            assertThat(service.isReady()).isTrue();
        }
        @Test void setReadyFalse_isReadyReturnsFalse() {
            service.setReady(true);
            service.setReady(false);
            assertThat(service.isReady()).isFalse();
        }
    }
}
