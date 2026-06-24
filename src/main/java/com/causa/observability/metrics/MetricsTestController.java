package com.causa.observability.metrics;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller to manually trigger metrics for Datadog verification
 * TODO: Remove this in production
 */
@Path("/api/test/metrics")
public class MetricsTestController {

    @Inject
    CausaMetrics causaMetrics;

    @POST
    @Path("/generate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateTestMetrics() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate some successful analyses with different types and namespaces
            causaMetrics.recordCompleted("pod_crash", "production", 2.5);
            causaMetrics.recordCompleted("high_cpu", "staging", 1.8);
            causaMetrics.recordCompleted("oom", "production", 3.2);
            causaMetrics.recordCompleted("pod_crash", "test-app", 2.1);

            // Generate some failures with different reasons
            causaMetrics.recordFailed("llm_error", "pod_crash", "production");
            causaMetrics.recordFailed("data_unavailable", "high_cpu", "staging");
            causaMetrics.recordFailed("timeout", "oom", "production");
            causaMetrics.recordFailed("llm_error", "pod_crash", "test-app");
            causaMetrics.recordFailed("data_unavailable", "high_cpu", "test-app");

            // Generate more successful ones
            for (int i = 0; i < 10; i++) {
                causaMetrics.recordCompleted("pod_crash", "production", 2.0 + (i * 0.1));
            }

            for (int i = 0; i < 5; i++) {
                causaMetrics.recordCompleted("high_cpu", "staging", 1.5 + (i * 0.2));
            }

            result.put("status", "success");
            result.put("message", "Generated test metrics");
            result.put("metrics_generated", Map.of(
                "completed_analyses", 19,
                "failed_analyses", 5,
                "total_analyses", 24
            ));

            return Response.ok(result).build();

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/generate-failures")
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateFailures() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate a spike in failures for testing high failure rate monitor
            for (int i = 0; i < 20; i++) {
                causaMetrics.recordFailed("llm_error", "pod_crash", "production");
            }

            for (int i = 0; i < 15; i++) {
                causaMetrics.recordFailed("timeout", "high_cpu", "staging");
            }

            result.put("status", "success");
            result.put("message", "Generated 35 failure metrics");

            return Response.ok(result).build();

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/generate-slow")
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateSlowAnalyses() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate slow analyses for testing latency monitor
            for (int i = 0; i < 10; i++) {
                causaMetrics.recordCompleted("pod_crash", "production", 15.0 + (i * 0.5));
            }

            for (int i = 0; i < 10; i++) {
                causaMetrics.recordCompleted("high_cpu", "staging", 12.0 + (i * 0.3));
            }

            result.put("status", "success");
            result.put("message", "Generated 20 slow analysis metrics (12-20 seconds)");

            return Response.ok(result).build();

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }
}
