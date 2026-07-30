package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.core.domain.Alert;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AlertEntityMapper Tests")
class AlertEntityMapperTest {

    static Alert buildAlert() {
        return Alert.builder()
            .alertId("alrt_test00000001234")
            .sourceAlertId("fingerprint-abc")
            .alertName("HighMemory")
            .alertTimestamp(Instant.parse("2024-01-15T10:30:00Z"))
            .severity(AlertSeverity.CRITICAL)
            .status(AlertStatus.ACCEPTED)
            .workloadInfo(Alert.WorkloadInfo.of("pod-1","container-1","production","cluster-1","Deployment"))
            .workloadName("container-1")
            .alertMetadata(Alert.AlertMetadata.of(
                Map.of("env","prod"), Map.of("note","test"), "prometheus"))
            .build();
    }

    @Nested @DisplayName("toEntity() Tests")
    class ToEntityTests {
        @Test void nullReturnsNull()     { assertThat(AlertEntityMapper.toEntity(null)).isNull(); }
        @Test void idMapped()            { assertThat(AlertEntityMapper.toEntity(buildAlert()).getId()).isEqualTo("alrt_test00000001234"); }
        @Test void alertNameMapped()     { assertThat(AlertEntityMapper.toEntity(buildAlert()).getAlertName()).isEqualTo("HighMemory"); }
        @Test void severityMapped()      { assertThat(AlertEntityMapper.toEntity(buildAlert()).getSeverity()).isEqualTo("critical"); }
        @Test void statusIsAccepted()    { assertThat(AlertEntityMapper.toEntity(buildAlert()).getStatus()).isEqualTo("ACCEPTED"); }
        @Test void workloadNameMapped()  { assertThat(AlertEntityMapper.toEntity(buildAlert()).getWorkloadName()).isEqualTo("container-1"); }
        @Test void workloadInfoJsonSet() { assertThat(AlertEntityMapper.toEntity(buildAlert()).getWorkloadInfo()).isNotNull(); }
        @Test void alertMetadataJsonSet(){ assertThat(AlertEntityMapper.toEntity(buildAlert()).getAlertMetadata()).isNotNull(); }
        @Test void timestampMapped()     { assertThat(AlertEntityMapper.toEntity(buildAlert()).getAlertTimestamp()).isNotNull(); }
    }

    @Nested @DisplayName("toEntityWithStatus() Tests")
    class ToEntityWithStatusTests {
        @Test void rejectedStatusSet() {
            AlertEntity e = AlertEntityMapper.toEntityWithStatus(buildAlert(), "REJECTED", "too old");
            assertThat(e.getStatus()).isEqualTo("REJECTED");
            assertThat(e.getAlertMetadata().path("rejection_reason").asText()).isEqualTo("too old");
        }
        @Test void noRejectionReason_noFieldInMeta() {
            AlertEntity e = AlertEntityMapper.toEntityWithStatus(buildAlert(), "ACCEPTED", null);
            assertThat(e.getAlertMetadata().has("rejection_reason")).isFalse();
        }
    }

    @Nested @DisplayName("toDomain() Tests")
    class ToDomainTests {
        @Test void nullReturnsNull() { assertThat(AlertEntityMapper.toDomain(null)).isNull(); }

        @Test void roundTrip() {
            Alert original = buildAlert();
            AlertEntity entity = AlertEntityMapper.toEntity(original);
            Alert restored = AlertEntityMapper.toDomain(entity);

            assertThat(restored.getAlertId()).isEqualTo(original.getAlertId());
            assertThat(restored.getAlertName()).isEqualTo(original.getAlertName());
            assertThat(restored.getWorkloadName()).isEqualTo(original.getWorkloadName());
        }

        @Test void entityWithNullWorkloadInfo_usesDenormalisedColumn() {
            AlertEntity e = AlertEntityMapper.toEntity(buildAlert());
            e.setWorkloadInfo(null);
            Alert a = AlertEntityMapper.toDomain(e);
            assertThat(a.getWorkloadName()).isEqualTo("container-1");
        }

        @Test void entityWithNullAlertMetadata_usesDefaults() {
            AlertEntity e = AlertEntityMapper.toEntity(buildAlert());
            e.setAlertMetadata(null);
            Alert a = AlertEntityMapper.toDomain(e);
            assertThat(a.getAlertMetadata().alertSource()).isEqualTo("prometheus");
        }

        @Test void nullAlertTimestamp_isNull() {
            Alert a = Alert.builder()
                .alertId("alrt_notimestamp00000").alertName("N")
                .severity(AlertSeverity.CRITICAL).status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(null,null,null,null,null))
                .workloadName("w").build();
            AlertEntity e = AlertEntityMapper.toEntity(a);
            Alert restored = AlertEntityMapper.toDomain(e);
            assertThat(restored.getAlertTimestamp()).isNull();
        }
    }
}
