package com.causa.core.domain;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.common.utils.IdUtils;

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
    /** Typed RCA — null until the pipeline reaches VALIDATING. Serialisation to/from DB is handled exclusively by {@link com.causa.infrastructure.persistence.mappers.DiagnosticEntityMapper}. */
    private final RootCauseAnalysis rca;
    private final String validationResult;
    private final String validationData;

    private Diagnostic(Builder builder) {
        this.diagnosticId    = Objects.requireNonNull(builder.diagnosticId, "diagnosticId cannot be null");
        this.alertId         = Objects.requireNonNull(builder.alertId, "alertId cannot be null");
        this.status          = Objects.requireNonNull(builder.status, "status cannot be null");
        this.generatedAt     = Objects.requireNonNull(builder.generatedAt, "generatedAt cannot be null");
        this.confidenceScore = builder.confidenceScore;
        this.faultDomain     = builder.faultDomain;
        this.rca             = builder.rca;
        this.validationResult = builder.validationResult;
        this.validationData  = builder.validationData;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getDiagnosticId()         { return diagnosticId; }
    public String getAlertId()              { return alertId; }
    public DiagnosticStatus getStatus()     { return status; }
    public Instant getGeneratedAt()         { return generatedAt; }
    public Float getConfidenceScore()       { return confidenceScore; }
    public FaultDomain getFaultDomain()     { return faultDomain; }
    /** Typed RCA result — null until pipeline reaches VALIDATING/COMPLETED. */
    public RootCauseAnalysis getRca()       { return rca; }
    public String getValidationResult()     { return validationResult; }
    public String getValidationData()       { return validationData; }

    /**
     * Generates a unique diagnostic ID: {@code diag_<16-char-alphanumeric>}.
     * Total length = 21 chars, matching the {@code VARCHAR(21)} PK column.
     *
     * @param alertId   unused — kept for call-site compatibility
     * @param timestamp unused — kept for call-site compatibility
     * @return a new unique diagnostic ID
     */
    public static String generateDiagnosticId(String alertId, Instant timestamp) {
        return IdUtils.generateDiagnosticId();
    }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String diagnosticId;
        private String alertId;
        private DiagnosticStatus status;
        private Instant generatedAt;
        private Float confidenceScore;
        private FaultDomain faultDomain;
        private RootCauseAnalysis rca;
        private String validationResult;
        private String validationData;

        private Builder() {}

        public Builder diagnosticId(String v)        { this.diagnosticId = v;    return this; }
        public Builder alertId(String v)              { this.alertId = v;         return this; }
        public Builder status(DiagnosticStatus v)     { this.status = v;          return this; }
        public Builder generatedAt(Instant v)         { this.generatedAt = v;     return this; }
        public Builder confidenceScore(Float v)       { this.confidenceScore = v; return this; }
        public Builder faultDomain(FaultDomain v)     { this.faultDomain = v;     return this; }
        public Builder rca(RootCauseAnalysis v)       { this.rca = v;             return this; }
        public Builder validationResult(String v)     { this.validationResult = v; return this; }
        public Builder validationData(String v)       { this.validationData = v;  return this; }

        public Diagnostic build() { return new Diagnostic(this); }
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
