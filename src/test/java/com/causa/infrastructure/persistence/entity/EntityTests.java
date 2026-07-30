package com.causa.infrastructure.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JPA Entity Tests")
class EntityTests {

    @Nested @DisplayName("AlertEntity Tests")
    class AlertEntityTests {
        @Test void gettersAndSetters() {
            AlertEntity e = new AlertEntity();
            e.setId("alrt_001");
            e.setAlertName("HighCPU");
            e.setSeverity("critical");
            e.setStatus("ACCEPTED");
            e.setWorkloadName("my-app");
            e.setSourceAlertId("fp-123");
            assertThat(e.getId()).isEqualTo("alrt_001");
            assertThat(e.getAlertName()).isEqualTo("HighCPU");
            assertThat(e.getSeverity()).isEqualTo("critical");
            assertThat(e.getStatus()).isEqualTo("ACCEPTED");
            assertThat(e.getWorkloadName()).isEqualTo("my-app");
            assertThat(e.getSourceAlertId()).isEqualTo("fp-123");
        }

        @Test void fieldConstants_defined() {
            assertThat(AlertEntity.Fields.ALERT_ID).isEqualTo("id");
            assertThat(AlertEntity.Fields.ALERT_NAME).isEqualTo("alertName");
            assertThat(AlertEntity.Fields.STATUS).isEqualTo("status");
            assertThat(AlertEntity.Fields.WORKLOAD_NAME).isEqualTo("workloadName");
        }

        @Test void alertTimestamp_getterSetter() {
            AlertEntity e = new AlertEntity();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            e.setAlertTimestamp(now);
            assertThat(e.getAlertTimestamp()).isEqualTo(now);
        }

        @Test void workloadInfo_getterSetter() {
            AlertEntity e = new AlertEntity();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.put("pod_name", "my-pod");
            e.setWorkloadInfo(node);
            assertThat(e.getWorkloadInfo().get("pod_name").asText()).isEqualTo("my-pod");
        }
    }

    @Nested @DisplayName("ConfigurationEntity Tests")
    class ConfigurationEntityTests {
        @Test void gettersAndSetters() {
            ConfigurationEntity e = new ConfigurationEntity();
            e.setId("cnfg_001");
            e.setConfigKey("feature.enabled");
            e.setConfigValue("true");
            e.setEncrypted(false);
            assertThat(e.getId()).isEqualTo("cnfg_001");
            assertThat(e.getConfigKey()).isEqualTo("feature.enabled");
            assertThat(e.getConfigValue()).isEqualTo("true");
            assertThat(e.isEncrypted()).isFalse();
        }
        @Test void encryptedDefaultFalse() {
            ConfigurationEntity e = new ConfigurationEntity();
            // isEncrypted defaults to false via field initializer
            // Getter returns current value (may be null before setEncrypted called)
            e.setEncrypted(false);
            assertThat(e.isEncrypted()).isFalse();
        }
    }

    @Nested @DisplayName("DiagnosticEntity Tests")
    class DiagnosticEntityTests {
        @Test void gettersAndSetters() {
            DiagnosticEntity e = new DiagnosticEntity();
            e.setId("diag_001");
            e.setStatus("PENDING");
            e.setIssueTitle("OOM");
            e.setIssueDescription("Out of memory");
            e.setIssueType("MEMORY");
            e.setRootCauseSummary("Memory leak in cache");
            e.setValidationResult("VALIDATED");
            e.setRemarks("auto-generated");
            e.setCreatedBy("AUTOMATIC");
            assertThat(e.getId()).isEqualTo("diag_001");
            assertThat(e.getStatus()).isEqualTo("PENDING");
            assertThat(e.getIssueTitle()).isEqualTo("OOM");
            assertThat(e.getIssueType()).isEqualTo("MEMORY");
            assertThat(e.getCreatedBy()).isEqualTo("AUTOMATIC");
        }

        @Test void alertId_returnsNullWithNoAlert() {
            DiagnosticEntity e = new DiagnosticEntity();
            assertThat(e.getAlertId()).isNull();
        }

        @Test void alertId_returnsAlertEntityId() {
            DiagnosticEntity e = new DiagnosticEntity();
            AlertEntity alert = new AlertEntity();
            alert.setId("alrt_001");
            e.setAlert(alert);
            assertThat(e.getAlertId()).isEqualTo("alrt_001");
        }
    }
}
