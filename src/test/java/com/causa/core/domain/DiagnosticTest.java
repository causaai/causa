package com.causa.core.domain;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Diagnostic Domain Tests")
class DiagnosticTest {

    static Diagnostic minimal() {
        return Diagnostic.builder()
            .diagnosticId("diag_test0000000001")
            .alertId("alrt_test00000001234")
            .status(DiagnosticStatus.PENDING)
            .generatedAt(Instant.now())
            .build();
    }

    @Nested @DisplayName("Builder Tests")
    class BuilderTests {
        @Test void buildsSuccessfully()  { assertThat(minimal()).isNotNull(); }
        @Test void diagnosticIdIsSet()   { assertThat(minimal().getDiagnosticId()).isEqualTo("diag_test0000000001"); }
        @Test void alertIdIsSet()        { assertThat(minimal().getAlertId()).isEqualTo("alrt_test00000001234"); }
        @Test void statusIsSet()         { assertThat(minimal().getStatus()).isEqualTo(DiagnosticStatus.PENDING); }
        @Test void optionalFieldsNullByDefault() {
            assertThat(minimal().getConfidenceScore()).isNull();
            assertThat(minimal().getFaultDomain()).isNull();
            assertThat(minimal().getRca()).isNull();
        }

        @Test void nullDiagnosticIdThrows() {
            assertThatThrownBy(() -> Diagnostic.builder()
                .alertId("a").status(DiagnosticStatus.PENDING).generatedAt(Instant.now()).build())
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested @DisplayName("generateDiagnosticId() Tests")
    class GenerateIdTests {
        @Test void startsWithDiagPrefix() {
            assertThat(Diagnostic.generateDiagnosticId("any", Instant.now())).startsWith("diag_");
        }
        @Test void hasLength21() {
            assertThat(Diagnostic.generateDiagnosticId("any", Instant.now())).hasSize(21);
        }
    }

    @Nested @DisplayName("equals/hashCode Tests")
    class EqualsTests {
        @Test void sameId_equal() {
            Diagnostic a = minimal(), b = minimal();
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
        @Test void differentId_notEqual() {
            Diagnostic a = minimal();
            Diagnostic b = Diagnostic.builder().diagnosticId("diag_other000000000")
                .alertId("alrt_test00000001234").status(DiagnosticStatus.PENDING)
                .generatedAt(Instant.now()).build();
            assertThat(a).isNotEqualTo(b);
        }
        @Test void notEqualToNull() { assertThat(minimal()).isNotEqualTo(null); }
        @Test void notEqualToOtherType() { assertThat(minimal()).isNotEqualTo("string"); }
    }

    @Test void toString_containsDiagnosticId() {
        assertThat(minimal().toString()).contains("diag_test0000000001");
    }
}
