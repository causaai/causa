package com.causa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Diagnostic JPA Entity
 *
 * <p>Database entity for storing diagnostic analysis results in the persistence layer.
 * <p>Uses JSONB for storing root_cause_analysis as structured JSON.
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "diagnostics")
public class DiagnosticEntity extends BaseEntity {

    // Database column name constants
    public static final class Columns {
        private Columns() {}

        public static final String DIAGNOSTIC_ID = "diagnostic_id";
        public static final String ALERT_ID = "alert_id";
        public static final String STATUS = "status";
        public static final String GENERATED_AT = "generated_at";
        public static final String CONFIDENCE_SCORE = "confidence_score";
        public static final String FAULT_DOMAIN = "fault_domain";
        public static final String ROOT_CAUSE_ANALYSIS = "root_cause_analysis";
    }

    // Field name constants for Panache queries
    public static final class Fields {
        private Fields() {}

        public static final String DIAGNOSTIC_ID = "diagnosticId";
        public static final String ALERT = "alert";
        public static final String ALERT_ID = "alert.alertId";
        public static final String STATUS = "status";
        public static final String GENERATED_AT = "generatedAt";
        public static final String FAULT_DOMAIN = "faultDomain";
    }

    @Id
    @Column(name = Columns.DIAGNOSTIC_ID, nullable = false, length = 512)
    private String diagnosticId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Columns.ALERT_ID, nullable = false, foreignKey = @ForeignKey(name = "fk_diagnostic_alert"))
    private AlertEntity alert;

    @Column(name = Columns.STATUS, nullable = false, length = 20)
    private String status;

    @Column(name = Columns.GENERATED_AT, nullable = false)
    private Instant generatedAt;

    @Column(name = Columns.CONFIDENCE_SCORE)
    private Float confidenceScore;

    @Column(name = Columns.FAULT_DOMAIN, length = 20)
    private String faultDomain;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = Columns.ROOT_CAUSE_ANALYSIS, columnDefinition = "jsonb")
    private JsonNode rootCauseAnalysis;

    // Getters and Setters

    public String getDiagnosticId() {
        return diagnosticId;
    }

    public void setDiagnosticId(String diagnosticId) {
        this.diagnosticId = diagnosticId;
    }

    public AlertEntity getAlert() {
        return alert;
    }

    public void setAlert(AlertEntity alert) {
        this.alert = alert;
    }

    /**
     * Helper method to get alert ID without loading the Alert entity.
     *
     * @return the alert ID
     */
    public String getAlertId() {
        return alert != null ? alert.getAlertId() : null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Float getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getFaultDomain() {
        return faultDomain;
    }

    public void setFaultDomain(String faultDomain) {
        this.faultDomain = faultDomain;
    }

    public JsonNode getRootCauseAnalysis() {
        return rootCauseAnalysis;
    }

    public void setRootCauseAnalysis(JsonNode rootCauseAnalysis) {
        this.rootCauseAnalysis = rootCauseAnalysis;
    }
}
