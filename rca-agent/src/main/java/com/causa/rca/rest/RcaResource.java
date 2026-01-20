package com.causa.rca.rest;

import com.causa.rca.model.RcaReport;
import com.causa.rca.service.RcaOrchestrator;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST API resource for Root Cause Analysis operations.
 * <p>
 * This resource provides HTTP endpoints for triggering RCA analysis on Kubernetes pods.
 * It serves as the entry point for external clients to request diagnostic analysis
 * of pod issues and receive structured RCA reports.
 * </p>
 * <p>
 * All endpoints produce and consume JSON format for easy integration with various clients.
 * </p>
 *
 * @see RcaOrchestrator
 * @see RcaReport
 */
@Path("/rca")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RcaResource {

    @Inject
    RcaOrchestrator orchestrator;

    /**
     * Triggers a comprehensive Root Cause Analysis for a specific pod.
     * <p>
     * Initiates the complete RCA pipeline including:
     * <ol>
     *   <li>Data collection (metrics, logs, events, JFR data)</li>
     *   <li>Anomaly detection using AI</li>
     *   <li>Root cause analysis with detailed reasoning</li>
     *   <li>Validation and formatting of results</li>
     * </ol>
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * GET /rca/analyze?namespace=production&pod=my-app-pod-12345
     * </pre>
     * </p>
     *
     * @param namespace the Kubernetes namespace where the pod is located (defaults to "default")
     * @param pod the name of the pod to analyze (required)
     * @return an {@link RcaReport} containing the complete analysis results including
     *         issue description, evidence, logs, proposed solution, and confidence score
     * @throws BadRequestException if the pod parameter is null or empty
     */
    @GET
    @Path("/analyze")
    public RcaReport analyze(
            @QueryParam("namespace") @DefaultValue("default") String namespace,
            @QueryParam("pod") String pod) {
        if (pod == null || pod.isEmpty()) {
            throw new BadRequestException("Pod name is required");
        }
        return orchestrator.runAnalysis(namespace, pod);
    }
}
