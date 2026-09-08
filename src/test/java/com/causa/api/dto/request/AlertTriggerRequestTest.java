package com.causa.api.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlertTriggerRequest}.
 *
 * @since 0.0.1
 */
@DisplayName("AlertTriggerRequest Tests")
class AlertTriggerRequestTest {

    @Test
    @DisplayName("Should correctly set and get namespace")
    void shouldSetAndGetNamespace() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setNamespace("production");
        assertEquals("production", req.getNamespace());
    }

    @Test
    @DisplayName("Should correctly set and get container")
    void shouldSetAndGetContainer() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setContainer("my-container");
        assertEquals("my-container", req.getContainer());
    }

    @Test
    @DisplayName("Should correctly set and get pod")
    void shouldSetAndGetPodName() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setPodName("my-pod-abc123");
        assertEquals("my-pod-abc123", req.getPodName());
    }

    @Test
    @DisplayName("Should correctly set and get workload_name")
    void shouldSetAndGetWorkloadName() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setWorkloadName("my-service");
        assertEquals("my-service", req.getWorkloadName());
    }

    @Test
    @DisplayName("Should correctly set and get workload_type")
    void shouldSetAndGetWorkloadType() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setWorkloadType("Deployment");
        assertEquals("Deployment", req.getWorkloadType());
    }

    @Test
    @DisplayName("Should correctly set and get cluster_name")
    void shouldSetAndGetClusterName() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setClusterName("prod-cluster");
        assertEquals("prod-cluster", req.getClusterName());
    }

    @Test
    @DisplayName("Should correctly set and get severity")
    void shouldSetAndGetSeverity() {
        AlertTriggerRequest req = new AlertTriggerRequest();
        req.setSeverity("warning");
        assertEquals("warning", req.getSeverity());
    }

    @Test
    @DisplayName("Should have all fields null on default construction")
    void shouldHaveAllNullFieldsByDefault() {
        AlertTriggerRequest req = new AlertTriggerRequest();

        assertNull(req.getNamespace());
        assertNull(req.getContainer());
        assertNull(req.getPodName());
        assertNull(req.getWorkloadName());
        assertNull(req.getWorkloadType());
        assertNull(req.getClusterName());
        assertNull(req.getSeverity());
    }
}
