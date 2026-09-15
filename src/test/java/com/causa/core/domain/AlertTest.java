package com.causa.core.domain;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Alert Domain Tests")
class AlertTest {

    static Alert minimal() {
        return Alert.builder()
            .alertId("alrt_test00000001234")
            .alertName("HighMemory")
            .severity(AlertSeverity.CRITICAL)
            .status(AlertStatus.ACCEPTED)
            .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "ns", "cluster", "Deployment"))
            .workloadName("container-1")
            .build();
    }

    @Nested @DisplayName("Builder Tests")
    class BuilderTests {
        @Test void buildsSuccessfully()   { assertThat(minimal()).isNotNull(); }
        @Test void alertIdIsSet()         { assertThat(minimal().getAlertId()).isEqualTo("alrt_test00000001234"); }
        @Test void alertNameIsSet()       { assertThat(minimal().getAlertName()).isEqualTo("HighMemory"); }
        @Test void severityIsSet()        { assertThat(minimal().getSeverity()).isEqualTo(AlertSeverity.CRITICAL); }
        @Test void statusIsSet()          { assertThat(minimal().getStatus()).isEqualTo(AlertStatus.ACCEPTED); }
        @Test void workloadNameIsSet()    { assertThat(minimal().getWorkloadName()).isEqualTo("container-1"); }
        @Test void alertMetadataDefaults(){ assertThat(minimal().getAlertMetadata()).isNotNull(); }

        @Test void nullAlertIdThrows() {
            assertThatThrownBy(() -> Alert.builder()
                .alertName("N").severity(AlertSeverity.CRITICAL).status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(null,null,null,null,null)).workloadName("w").build())
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested @DisplayName("getCooldownKey() Tests")
    class CooldownKeyTests {
        @Test void keyContainsAllParts() {
            String key = minimal().getCooldownKey();
            assertThat(key).contains("HighMemory").contains("cluster").contains("ns").contains("pod-1");
        }
        @Test void nullFieldsUseEmptyString() {
            Alert a = Alert.builder().alertId("alrt_x").alertName("N")
                .severity(AlertSeverity.WARNING).status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(null,null,null,null,null))
                .workloadName("w").build();
            assertThat(a.getCooldownKey()).doesNotContain("null");
        }
    }

    @Nested @DisplayName("equals/hashCode Tests")
    class EqualsTests {
        @Test void sameId_equal() {
            Alert a = minimal(), b = minimal();
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
        @Test void differentId_notEqual() {
            Alert a = minimal();
            Alert b = Alert.builder().alertId("alrt_other0000000000").alertName("N")
                .severity(AlertSeverity.CRITICAL).status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(null,null,null,null,null)).workloadName("w").build();
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested @DisplayName("WorkloadInfo Tests")
    class WorkloadInfoTests {
        @Test void ofCreatesRecord() {
            Alert.WorkloadInfo wi = Alert.WorkloadInfo.of("p","c","n","cl","Dep");
            assertThat(wi.podName()).isEqualTo("p");
            assertThat(wi.containerName()).isEqualTo("c");
            assertThat(wi.namespace()).isEqualTo("n");
        }
    }

    @Nested @DisplayName("AlertMetadata Tests")
    class MetadataTests {
        @Test void empty_hasDefaults() {
            Alert.AlertMetadata m = Alert.AlertMetadata.empty();
            assertThat(m.labels()).isEmpty();
            assertThat(m.alertSource()).isEqualTo("prometheus");
        }
        @Test void of_withNulls_usesDefaults() {
            Alert.AlertMetadata m = Alert.AlertMetadata.of(null, null, null);
            assertThat(m.alertSource()).isEqualTo("prometheus");
        }
        @Test void of_withValues() {
            Alert.AlertMetadata m = Alert.AlertMetadata.of(
                Map.of("k","v"), Map.of("a","b"), "custom");
            assertThat(m.labels()).containsEntry("k","v");
            assertThat(m.alertSource()).isEqualTo("custom");
        }
    }

    @Test void toString_containsAlertId() {
        assertThat(minimal().toString()).contains("alrt_test00000001234");
    }
}
