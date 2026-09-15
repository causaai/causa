package com.causa.common.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Exception Tests")
class ExceptionTests {

    // -------------------------------------------------------------------------
    // AlertException
    // -------------------------------------------------------------------------
    @Nested @DisplayName("AlertException Tests")
    class AlertExceptionTests {

        @Test void messageAndErrorType() {
            AlertException ex = new AlertException("msg", "TYPE");
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getErrorType()).isEqualTo("TYPE");
            assertThat(ex.getCause()).isNull();
        }

        @Test void messageErrorTypeAndCause() {
            RuntimeException cause = new RuntimeException("root");
            AlertException ex = new AlertException("msg", "TYPE", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test void wrapCauseDerivesErrorType() {
            RuntimeException cause = new RuntimeException("root");
            AlertException ex = new AlertException("msg", cause);
            assertThat(ex.getErrorType()).isEqualTo("RuntimeException");
        }

        @Test void wrapNullCauseUsesUnknown() {
            AlertException ex = new AlertException("msg", (Throwable) null);
            assertThat(ex.getErrorType()).isEqualTo("Unknown");
        }
    }

    // -------------------------------------------------------------------------
    // DiagnosticException
    // -------------------------------------------------------------------------
    @Nested @DisplayName("DiagnosticException Tests")
    class DiagnosticExceptionTests {

        @Test void messageAndErrorType() {
            DiagnosticException ex = new DiagnosticException("diag msg", "DIAG_TYPE");
            assertThat(ex.getMessage()).isEqualTo("diag msg");
            assertThat(ex.getErrorType()).isEqualTo("DIAG_TYPE");
        }

        @Test void messageErrorTypeAndCause() {
            Exception cause = new Exception("root");
            DiagnosticException ex = new DiagnosticException("msg", "T", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test void wrapCauseDerivesErrorType() {
            IllegalStateException cause = new IllegalStateException("state");
            DiagnosticException ex = new DiagnosticException("msg", cause);
            assertThat(ex.getErrorType()).isEqualTo("IllegalStateException");
        }

        @Test void wrapNullCauseUsesUnknown() {
            DiagnosticException ex = new DiagnosticException("msg", (Throwable) null);
            assertThat(ex.getErrorType()).isEqualTo("Unknown");
        }
    }

    // -------------------------------------------------------------------------
    // LLMException
    // -------------------------------------------------------------------------
    @Nested @DisplayName("LLMException Tests")
    class LLMExceptionTests {

        @Test void messageAndErrorType() {
            LLMException ex = new LLMException("llm msg", "LLM_TYPE");
            assertThat(ex.getMessage()).isEqualTo("llm msg");
            assertThat(ex.getErrorType()).isEqualTo("LLM_TYPE");
        }

        @Test void messageErrorTypeAndCause() {
            RuntimeException cause = new RuntimeException("root");
            LLMException ex = new LLMException("msg", "T", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test void wrapCauseDerivesErrorType() {
            NullPointerException cause = new NullPointerException("npe");
            LLMException ex = new LLMException("msg", cause);
            assertThat(ex.getErrorType()).isEqualTo("NullPointerException");
        }

        @Test void wrapNullCauseUsesUnknown() {
            LLMException ex = new LLMException("msg", (Throwable) null);
            assertThat(ex.getErrorType()).isEqualTo("Unknown");
        }
    }
}
