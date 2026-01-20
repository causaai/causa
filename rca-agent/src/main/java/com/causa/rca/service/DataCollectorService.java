package com.causa.rca.service;

import com.causa.rca.external.CryostatClient;
import com.causa.rca.external.PrometheusClient;
import com.fasterxml.jackson.databind.JsonNode;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for collecting diagnostic data from various sources.
 * <p>
 * This service aggregates data from multiple sources to provide comprehensive context
 * for root cause analysis:
 * <ul>
 *   <li>Prometheus metrics (CPU, memory usage and limits)</li>
 *   <li>Kubernetes pod status and container states</li>
 *   <li>Kubernetes events related to the pod</li>
 *   <li>Pod logs (current and previous container logs)</li>
 *   <li>JFR (Java Flight Recorder) analysis from Cryostat</li>
 * </ul>
 * </p>
 * <p>
 * The collected data is formatted into structured strings that can be consumed by
 * AI services for anomaly detection and root cause analysis.
 * </p>
 *
 * @see PrometheusClient
 * @see CryostatClient
 * @see RcaOrchestrator
 */
@ApplicationScoped
public class DataCollectorService {

    private static final Logger LOG = Logger.getLogger(DataCollectorService.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    @RestClient
    PrometheusClient prometheusClient;

    @Inject
    @RestClient
    CryostatClient cryostatClient;

    @Inject
    TokenProvider tokenProvider;

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    /**
     * Fetches comprehensive resource metrics for a pod from Prometheus and Kubernetes.
     * <p>
     * Collects real-time metrics including:
     * <ul>
     *   <li>Memory usage and limits (from container metrics and JVM fallback)</li>
     *   <li>CPU usage and limits</li>
     *   <li>Resource requests and limits from Kubernetes API</li>
     *   <li>Usage percentages relative to limits</li>
     * </ul>
     * </p>
     * <p>
     * The method uses PromQL queries targeting actual application containers (excluding
     * sidecar and init containers) and falls back to JVM metrics if container metrics
     * are unavailable.
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod
     * @return a formatted string containing detailed metrics summary with usage percentages,
     *         or an error message if collection fails
     */
    public String fetchMetrics(String namespace, String podName) {
        LOG.info(">>> Fetching Detailed Metrics for: " + namespace + "/" + podName);
        try {
            // 1. Get Pod info from K8s API
            Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
            String k8sLimits = "N/A";
            String k8sRequests = "N/A";
            if (pod != null && pod.getSpec() != null && !pod.getSpec().getContainers().isEmpty()) {
                var container = pod.getSpec().getContainers().get(0);
                k8sLimits = container.getResources().getLimits().toString();
                k8sRequests = container.getResources().getRequests().toString();
            }
            LOG.info("K8s API - Limits: " + k8sLimits + ", Requests: " + k8sRequests);

            // 2. Build PromQL queries for Usage, Limits, and Requests
            // We use 'container!=""' and 'image!=""' to target the actual application
            // container
            String currentMemQuery = String.format(
                    "sum(container_memory_usage_bytes{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace);
            String limitMemQuery = String.format(
                    "sum(container_spec_memory_limit_bytes{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace);
            String currentCpuQuery = String.format(
                    "sum(rate(container_cpu_usage_seconds_total{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"}[5m]))",
                    podName, namespace);
            String limitCpuQuery = String.format(
                    "sum(container_spec_cpu_quota{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"}) / sum(container_spec_cpu_period{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace, podName, namespace);

            LOG.debug("Mem Usage Query: " + currentMemQuery);
            LOG.debug("Mem Limit Query: " + limitMemQuery);

            JsonNode memUsageRes = prometheusClient.query(tokenProvider.getToken(), currentMemQuery);
            JsonNode memLimitRes = prometheusClient.query(tokenProvider.getToken(), limitMemQuery);
            JsonNode cpuUsageRes = prometheusClient.query(tokenProvider.getToken(), currentCpuQuery);
            JsonNode cpuLimitRes = prometheusClient.query(tokenProvider.getToken(), limitCpuQuery);

            LOG.debug("Prometheus Raw Responses:");
            LOG.debug("Mem Usage: " + memUsageRes);
            LOG.debug("Mem Limit: " + memLimitRes);

            double memUsageBytes = extractValue(memUsageRes);
            double memLimitBytes = extractValue(memLimitRes);
            double cpuUsageCores = extractValue(cpuUsageRes);
            double cpuLimitCores = extractValue(cpuLimitRes);

            LOG.info(String.format("Extracted Metrics - MemUsage: %.0f, MemLimit: %.0f, CpuUsage: %.3f, CpuLimit: %.3f",
                    memUsageBytes, memLimitBytes, cpuUsageCores, cpuLimitCores));

            // 3. Fallback to JVM metrics if container metrics are missing/0
            if (memUsageBytes == 0.0) {
                LOG.info("Container memory metrics returned 0, attempting JVM fallback...");
                String jvmMemQuery = String.format(
                        "sum(jvm_memory_used_bytes{pod=\"%s\", namespace=\"%s\", area=\"heap\"})", podName, namespace);
                memUsageBytes = extractValue(prometheusClient.query(tokenProvider.getToken(), jvmMemQuery));
                LOG.info("JVM Fallback Mem Usage: " + memUsageBytes);
            }

            // 4. Calculate Percentages
            double memPercent = (memLimitBytes > 0) ? (memUsageBytes / memLimitBytes) * 100 : 0;
            double cpuPercent = (cpuLimitCores > 0) ? (cpuUsageCores / cpuLimitCores) * 100 : 0;

            String summary = String.format("""
                    --- DETAILED RESOURCE METRICS ---
                    TARGET: %s/%s

                    K8S RESOURCE CONFIG:
                      Limits:   %s
                      Requests: %s

                    PROMETHEUS REAL-TIME DATA:
                      Memory Usage: %.2f MB (%.2f%% of limit)
                      Memory Limit: %.2f MB
                      CPU Usage:    %.3f Cores (%.2f%% of limit)
                      CPU Limit:    %.3f Cores
                    ---
                    """, namespace, podName,
                    k8sLimits.replace("{", "[").replace("}", "]"),
                    k8sRequests.replace("{", "[").replace("}", "]"),
                    memUsageBytes / (1024 * 1024), memPercent, memLimitBytes / (1024 * 1024),
                    cpuUsageCores, cpuPercent, cpuLimitCores);

            LOG.info("Metric Collection Success for " + podName);
            return summary;
        } catch (Exception e) {
            LOG.error("Significant error during Prometheus metric collection for " + podName, e);
            return "Error fetching detailed metrics: " + e.getMessage();
        }
    }

    /**
     * Extracts a numeric value from a Prometheus query response.
     * <p>
     * Parses the JSON response structure from Prometheus API and extracts the metric value.
     * Returns 0.0 if the response is empty or malformed.
     * </p>
     *
     * @param result the JsonNode containing the Prometheus query response
     * @return the extracted metric value, or 0.0 if extraction fails
     */
    private double extractValue(JsonNode result) {
        try {
            if (result.has("data") && result.get("data").has("result") && result.get("data").get("result").size() > 0) {
                return result.get("data").get("result").get(0).get("value").get(1).asDouble();
            }
        } catch (Exception e) {
            LOG.warn("Could not extract value from Prometheus response", e);
        }
        return 0.0;
    }

    /**
     * Fetches Java Flight Recorder (JFR) analysis report from Cryostat.
     * <p>
     * Retrieves detailed JVM profiling data including CPU usage, memory allocation,
     * thread activity, garbage collection statistics, and other JVM-level metrics.
     * This data is crucial for diagnosing Java application issues.
     * </p>
     * <p>
     * If Cryostat is disabled in configuration, this method returns a message
     * indicating JFR analysis is unavailable.
     * </p>
     *
     * @param target the target identifier (typically the pod name)
     * @return the JFR analysis report as a string, or an error/disabled message
     */
    public String fetchJfrAnalysis(String target) {
        if (!cryostatEnabled) {
            LOG.info("Cryostat is disabled. Skipping JFR analysis fetch.");
            return "JFR Analysis is disabled.";
        }
        LOG.info("Fetching JFR analysis from Cryostat for target: " + target);
        try {
            String report = cryostatClient.getReport(tokenProvider.getToken(), target);
            LOG.info("Gathered JFR report (length: " + (report != null ? report.length() : 0) + ")");
            return report;
        } catch (Exception e) {
            LOG.error("Failed to fetch Cryostat report", e);
            return "Error fetching JFR analysis: " + e.getMessage();
        }
    }

    /**
     * Fetches pod logs from Kubernetes.
     * <p>
     * Attempts to retrieve the last 500 lines of logs from the pod. If the current
     * container has no logs (e.g., after a restart), it attempts to fetch logs from
     * the previous terminated container instance.
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod
     * @return the pod logs as a string (up to 500 lines), or an error message if retrieval fails
     */
    public String fetchLogs(String namespace, String podName) {
        LOG.info("Fetching logs for pod: " + podName);
        try {
            // Try current logs first
            String logs = kubernetesClient.pods().inNamespace(namespace).withName(podName).tailingLines(500).getLog();
            if (logs == null || logs.trim().isEmpty()) {
                LOG.info("Current logs empty, attempting to fetch PREVIOUS logs for: " + podName);
                logs = kubernetesClient.pods().inNamespace(namespace).withName(podName).terminated().tailingLines(500)
                        .getLog();
            }
            LOG.info("Gathered logs (length: " + (logs != null ? logs.length() : 0) + ")");
            return (logs != null && !logs.isEmpty()) ? logs : "No logs available (even from terminated container)";
        } catch (Exception e) {
            LOG.error("Failed to fetch pod logs", e);
            return "Error fetching logs: " + e.getMessage();
        }
    }

    /**
     * Fetches Kubernetes events related to a specific pod.
     * <p>
     * Retrieves all events in the namespace and filters them to include only those
     * related to the specified pod. Events provide important information about pod
     * lifecycle, scheduling, health checks, and issues like OOMKilled, ImagePullBackOff, etc.
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod
     * @return a formatted string containing all relevant events with timestamps, types,
     *         reasons, and messages, or an error message if retrieval fails
     */
    public String fetchEvents(String namespace, String podName) {
        LOG.info("Fetching K8s Events for pod: " + podName);
        try {
            // Fetch events and filter for this pod
            // Use v1().events() for standard core V1 events which are namespaced
            List<Event> events = kubernetesClient.v1().events().inNamespace(namespace).list().getItems().stream()
                    .filter(e -> e.getInvolvedObject() != null && podName.equals(e.getInvolvedObject().getName()))
                    .collect(Collectors.toList());

            if (events.isEmpty()) {
                return "No events found for this pod.";
            }

            StringBuilder sb = new StringBuilder();
            for (Event e : events) {
                sb.append(String.format("[%s] Type: %s, Reason: %s, Message: %s\n",
                        e.getLastTimestamp(), e.getType(), e.getReason(), e.getMessage()));
            }
            LOG.info("Gathered " + events.size() + " events.");
            return sb.toString();
        } catch (Exception e) {
            LOG.error("Failed to fetch events", e);
            return "Error fetching events: " + e.getMessage();
        }
    }

    /**
     * Fetches detailed status information for a pod from Kubernetes.
     * <p>
     * Retrieves comprehensive pod status including:
     * <ul>
     *   <li>Pod phase (Running, Pending, Failed, etc.)</li>
     *   <li>Container readiness status</li>
     *   <li>Restart counts</li>
     *   <li>Current container state (Running, Waiting, Terminated)</li>
     *   <li>Last termination state with exit codes and reasons</li>
     * </ul>
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod
     * @return a formatted string containing detailed pod and container status information,
     *         or an error message if retrieval fails
     */
    public String fetchPodStatus(String namespace, String podName) {
        LOG.info("Fetching Kubernetes Pod Status for: " + podName);
        try {
            Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null)
                return "Pod not found";

            StringBuilder statusInfo = new StringBuilder();
            statusInfo.append("Phase: ").append(pod.getStatus().getPhase()).append("\n");

            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            for (ContainerStatus cs : containerStatuses) {
                statusInfo.append("Container: ").append(cs.getName()).append("\n");
                statusInfo.append("  Ready: ").append(cs.getReady()).append("\n");
                statusInfo.append("  Restart Count: ").append(cs.getRestartCount()).append("\n");

                if (cs.getState().getWaiting() != null) {
                    statusInfo.append("  Current State: Waiting (").append(cs.getState().getWaiting().getReason())
                            .append(")\n");
                    statusInfo.append("  Message: ").append(cs.getState().getWaiting().getMessage()).append("\n");
                }

                if (cs.getLastState().getTerminated() != null) {
                    statusInfo.append("  Last State: Terminated (")
                            .append(cs.getLastState().getTerminated().getReason()).append(")\n");
                    statusInfo.append("  Exit Code: ").append(cs.getLastState().getTerminated().getExitCode())
                            .append("\n");
                    statusInfo.append("  Finished At: ").append(cs.getLastState().getTerminated().getFinishedAt())
                            .append("\n");
                }
            }
            return statusInfo.toString();
        } catch (Exception e) {
            LOG.error("Failed to fetch pod status", e);
            return "Error fetching pod status: " + e.getMessage();
        }
    }

    /**
     * Collects and packages all diagnostic data for a pod.
     * <p>
     * This is the main orchestration method that gathers data from all sources:
     * <ul>
     *   <li>Pod status from Kubernetes</li>
     *   <li>Kubernetes events</li>
     *   <li>Resource metrics from Prometheus</li>
     *   <li>Pod logs</li>
     *   <li>JFR analysis from Cryostat (if enabled)</li>
     * </ul>
     * </p>
     * <p>
     * The collected data is formatted into two strings:
     * <ul>
     *   <li><b>metrics_data</b>: Summary of resource metrics</li>
     *   <li><b>full_context</b>: Complete formatted context with all collected data</li>
     * </ul>
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod
     * @return a Map containing "metrics_data" and "full_context" keys with their respective
     *         formatted data strings ready for AI analysis
     */
    public Map<String, String> getRealDataPackage(String namespace, String podName) {
        LOG.info("Starting data collection package for: " + namespace + "/" + podName);
        String metricsData = fetchMetrics(namespace, podName);
        String logsData = fetchLogs(namespace, podName);
        String podStatusData = fetchPodStatus(namespace, podName);
        String eventsData = fetchEvents(namespace, podName);
        String jfrData = fetchJfrAnalysis(podName);

        String fullContext = String.format("""
                --- POD STATUS ---
                %s

                --- K8S EVENTS ---
                %s

                --- METRICS ---
                %s

                --- LOGS (Tail) ---
                %s

                --- JFR ANALYSIS ---
                %s
                """, podStatusData, eventsData, metricsData, logsData, jfrData);

        return Map.of(
                "metrics_data", metricsData,
                "full_context", fullContext);
    }
}
