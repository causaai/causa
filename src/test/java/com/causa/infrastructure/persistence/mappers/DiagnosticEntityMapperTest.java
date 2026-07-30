package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DiagnosticEntityMapper Tests")
class DiagnosticEntityMapperTest {

    static Diagnostic minimal() {
        return Diagnostic.builder()
            .diagnosticId("diag_test0000000001")
            .alertId("alrt_test00000001234")
            .status(DiagnosticStatus.COMPLETED)
            .generatedAt(Instant.now())
            .confidenceScore(0.92f)
            .faultDomain(FaultDomain.OOM_KILLED)
            .validationResult("VALIDATED")
            .build();
    }

    static Diagnostic withRca() {
        RootCauseAnalysis.ConfidenceSummary cs = new RootCauseAnalysis.ConfidenceSummary(0.9, "high");
        RootCauseAnalysis rca = new RootCauseAnalysis(
            "OOM Issue", "Summary text", "Desc", "Tech desc",
            RootCauseAnalysis.AnomalyType.OOM_KILLED, "memory leak",
            List.of("log1"), List.of("ev1"),
            List.of(new RootCauseAnalysis.Recommendation("Immediate Mitigation","fix","desc","notes",0.9,List.of())),
            cs, "notes"
        );
        return Diagnostic.builder()
            .diagnosticId("diag_rca00000000001")
            .alertId("alrt_test00000001234")
            .status(DiagnosticStatus.COMPLETED)
            .generatedAt(Instant.now())
            .rca(rca)
            .validationData("{\"key\":\"val\"}")
            .build();
    }

    @Nested @DisplayName("toEntity() Tests")
    class ToEntityTests {
        @Test void nullReturnsNull()       { assertThat(DiagnosticEntityMapper.toEntity(null)).isNull(); }
        @Test void idMapped()              { assertThat(DiagnosticEntityMapper.toEntity(minimal()).getId()).isEqualTo("diag_test0000000001"); }
        @Test void statusMapped()          { assertThat(DiagnosticEntityMapper.toEntity(minimal()).getStatus()).isEqualTo("COMPLETED"); }
        @Test void confidenceInfoSet()     { assertThat(DiagnosticEntityMapper.toEntity(minimal()).getConfidenceInfo()).isNotNull(); }
        @Test void issueTypeMapped()       { assertThat(DiagnosticEntityMapper.toEntity(minimal()).getIssueType()).isEqualTo("OOM_KILLED"); }
        @Test void validationResultMapped(){ assertThat(DiagnosticEntityMapper.toEntity(minimal()).getValidationResult()).isEqualTo("VALIDATED"); }
        @Test void rcaSerialized() {
            DiagnosticEntity e = DiagnosticEntityMapper.toEntity(withRca());
            assertThat(e.getRootCauseSummary()).contains("OOM Issue");
            assertThat(e.getIssueTitle()).isEqualTo("OOM Issue");
            assertThat(e.getIssueDescription()).isEqualTo("Desc");
        }
        @Test void validationDataParsed() {
            DiagnosticEntity e = DiagnosticEntityMapper.toEntity(withRca());
            assertThat(e.getValidationData()).isNotNull();
        }
    }

    @Nested @DisplayName("toDomain() Tests")
    class ToDomainTests {
        @Test void nullReturnsNull() { assertThat(DiagnosticEntityMapper.toDomain(null)).isNull(); }

        @Test void roundTrip() {
            Diagnostic original = minimal();
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(original);
            // Simulate what DB would set for createdAt
            entity.createdAt = java.time.OffsetDateTime.now();
            Diagnostic restored = DiagnosticEntityMapper.toDomain(entity);

            assertThat(restored.getDiagnosticId()).isEqualTo(original.getDiagnosticId());
            assertThat(restored.getAlertId()).isEqualTo(original.getAlertId());
            assertThat(restored.getStatus()).isEqualTo(DiagnosticStatus.COMPLETED);
        }

        @Test void roundTrip_withRca() {
            Diagnostic original = withRca();
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(original);
            entity.createdAt = java.time.OffsetDateTime.now();
            Diagnostic restored = DiagnosticEntityMapper.toDomain(entity);
            assertThat(restored.getRca()).isNotNull();
            assertThat(restored.getRca().issueTitle()).isEqualTo("OOM Issue");
        }

        @Test void confidenceScore_restoredFromJsonb() {
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(minimal());
            entity.createdAt = java.time.OffsetDateTime.now();
            Diagnostic restored = DiagnosticEntityMapper.toDomain(entity);
            assertThat(restored.getConfidenceScore()).isCloseTo(0.92f, org.assertj.core.data.Offset.offset(0.001f));
        }

        @Test void malformedRcaJson_leavesRcaNull() {
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(minimal());
            entity.createdAt = java.time.OffsetDateTime.now();
            entity.setRootCauseSummary("{not-valid-json");
            Diagnostic restored = DiagnosticEntityMapper.toDomain(entity);
            assertThat(restored.getRca()).isNull();
        }

        @Test void unknownIssueType_isIgnored() {
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(minimal());
            entity.createdAt = java.time.OffsetDateTime.now();
            entity.setIssueType("UNKNOWN_DOMAIN");
            // should not throw
            assertThatCode(() -> DiagnosticEntityMapper.toDomain(entity)).doesNotThrowAnyException();
        }
    }
}
