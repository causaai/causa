package com.causa.api.controllers;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LivenessCheck Tests")
class LivenessCheckTest {

    LivenessCheck check = new LivenessCheck();

    @Test void alwaysReturnsUp() {
        assertThat(check.call().getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test void hasName() {
        assertThat(check.call().getName()).isNotBlank();
    }

    @Test void hasStatusData() {
        assertThat(check.call().getData()).isPresent();
    }
}
