package com.causa.api.controllers;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReadinessCheck Tests")
class ReadinessCheckTest {

    ReadinessCheck check = new ReadinessCheck();

    @Test void alwaysReturnsUp_currentImpl() {
        // Current implementation always returns true (TODO stub)
        assertThat(check.call().getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test void hasName() {
        assertThat(check.call().getName()).isNotBlank();
    }

    @Test void hasData() {
        assertThat(check.call().getData()).isPresent();
    }
}
