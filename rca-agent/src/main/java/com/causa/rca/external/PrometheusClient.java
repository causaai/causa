package com.causa.rca.external;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.HeaderParam;

/**
 * REST client interface for interacting with Prometheus API.
 * <p>
 * Prometheus is a monitoring and alerting toolkit that collects and stores metrics
 * as time series data. This client executes PromQL queries to retrieve real-time
 * metrics about pod resource usage, including CPU, memory, and other container metrics.
 * </p>
 * <p>
 * The client is configured using the "prometheus-api" configuration key in application.properties,
 * which should specify the base URL and connection parameters for the Prometheus service.
 * </p>
 *
 * @see <a href="https://prometheus.io/">Prometheus Project</a>
 */
@RegisterRestClient(configKey = "prometheus-api")
public interface PrometheusClient {

    /**
     * Executes a PromQL query against the Prometheus API.
     * <p>
     * Sends a PromQL (Prometheus Query Language) query to retrieve metrics data.
     * Common queries include container memory usage, CPU usage, resource limits,
     * and other time-series metrics essential for anomaly detection and root cause analysis.
     * </p>
     *
     * @param authHeader the authorization header containing the bearer token for authentication
     *                   (format: "Bearer {token}")
     * @param query the PromQL query string (e.g., "container_memory_usage_bytes{pod='my-pod'}")
     * @return a JsonNode containing the query results with the structure:
     *         {status: "success", data: {resultType: "vector", result: [{metric: {...}, value: [timestamp, value]}]}}
     */
    @GET
    @Path("/api/v1/query")
    JsonNode query(@HeaderParam("Authorization") String authHeader, @QueryParam("query") String query);
}
