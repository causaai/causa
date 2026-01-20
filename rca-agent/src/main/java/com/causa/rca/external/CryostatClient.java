package com.causa.rca.external;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.HeaderParam;

/**
 * REST client interface for interacting with Cryostat API.
 * <p>
 * Cryostat is a container-native JVM application that provides JFR (Java Flight Recorder)
 * monitoring and profiling capabilities for containerized Java applications. This client
 * retrieves JFR analysis reports for specific target pods.
 * </p>
 * <p>
 * The client is configured using the "cryostat-api" configuration key in application.properties,
 * which should specify the base URL and other connection parameters for the Cryostat service.
 * </p>
 *
 * @see <a href="https://cryostat.io/">Cryostat Project</a>
 */
@RegisterRestClient(configKey = "cryostat-api")
public interface CryostatClient {

    /**
     * Retrieves the JFR analysis report for a specific target pod.
     * <p>
     * Fetches the Java Flight Recorder report which contains detailed profiling information
     * including CPU usage, memory allocation, thread activity, garbage collection statistics,
     * and other JVM-level metrics that are crucial for root cause analysis.
     * </p>
     *
     * @param authHeader the authorization header containing the bearer token for authentication
     *                   (format: "Bearer {token}")
     * @param target the target identifier for the pod (typically the pod name)
     * @return the JFR analysis report as a string, containing detailed profiling data
     */
    @GET
    @Path("/api/v1/targets/{target}/reports")
    String getReport(@HeaderParam("Authorization") String authHeader, @PathParam("target") String target);
}
