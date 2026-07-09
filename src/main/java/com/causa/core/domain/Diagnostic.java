package com.causa.core.domain;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;

import java.time.Instant;
import java.util.Objects;

/**
 * Diagnostic Domain Model
 *
 * <p>Represents the LLM-based diagnostic analysis result for an alert.
 * <p>This is an immutable aggregate root in the core domain layer.
 *
 * @since 0.0.1
 */
public final class Diagnostic {

    private final String diagnosticId;
    private final String alertId;
    private final DiagnosticStatus status;
    private final Instant generatedAt;
    private final Float confidenceScore;
    private final FaultDomain faultDomain;
    private final String rootCauseAnalysis;  // Will be JSON string from LLM

    private Diagnostic(Builder builder) {
        this.diagnosticId = Objects.requireNonNull(builder.diagnosticId, "diagnosticId cannot be null");
        this.alertId = Objects.requireNonNull(builder.alertId, "alertId cannot be null");
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "generatedAt cannot be null");
        this.confidenceScore = builder.confidenceScore;  // nullable for PENDING status
        this.faultDomain = builder.faultDomain;  // nullable for PENDING status
        this.rootCauseAnalysis = builder.rootCauseAnalysis;  // nullable for PENDING status
    }

    // Getters

    public String getDiagnosticId() {
        return diagnosticId;
    }

    public String getAlertId() {
        return alertId;
    }

    public DiagnosticStatus getStatus() {
        return status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Float getConfidenceScore() {
        return confidenceScore;
    }

    public FaultDomain getFaultDomain() {
        return faultDomain;
    }

    public String getRootCauseAnalysis() {
        return rootCauseAnalysis;
    }

    /**
     * Generates a deterministic diagnostic ID from alert ID and timestamp.
     *
     * <p>Format: diag-{alertId}-{epochMillis}
     *
     * @param alertId the alert ID
     * @param timestamp the diagnostic timestamp
     * @return the generated diagnostic ID
     */
    public static String generateDiagnosticId(String alertId, Instant timestamp) {
        return "diag-" + alertId + "-" + timestamp.toEpochMilli();
    }

    /**
     * Creates a new builder for constructing Diagnostic instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing immutable Diagnostic instances.
     */
    public static final class Builder {
        private String diagnosticId;
        private String alertId;
        private DiagnosticStatus status;
        private Instant generatedAt;
        private Float confidenceScore;
        private FaultDomain faultDomain;
        private String rootCauseAnalysis;

        private Builder() {}

        public Builder diagnosticId(String diagnosticId) {
            this.diagnosticId = diagnosticId;
            return this;
        }

        public Builder alertId(String alertId) {
            this.alertId = alertId;
            return this;
        }

        public Builder status(DiagnosticStatus status) {
            this.status = status;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder confidenceScore(Float confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder faultDomain(FaultDomain faultDomain) {
            this.faultDomain = faultDomain;
            return this;
        }

        public Builder rootCauseAnalysis(String rootCauseAnalysis) {
            this.rootCauseAnalysis = rootCauseAnalysis;
            return this;
        }

        /**
         * Builds the Diagnostic instance.
         *
         * @return the constructed Diagnostic
         * @throws NullPointerException if any required field is null
         */
        public Diagnostic build() {
            return new Diagnostic(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Diagnostic that = (Diagnostic) o;
        return Objects.equals(diagnosticId, that.diagnosticId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diagnosticId);
    }

    @Override
    public String toString() {
        return "Diagnostic{" +
            "diagnosticId='" + diagnosticId + '\'' +
            ", alertId='" + alertId + '\'' +
            ", status=" + status +
            ", generatedAt=" + generatedAt +
            ", confidenceScore=" + confidenceScore +
            ", faultDomain=" + faultDomain +
            '}';
    }
}
