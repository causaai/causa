package com.causa.rca.service;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Service for automatically scanning and analyzing Kubernetes workloads.
 * <p>
 * This service periodically scans all pods across namespaces that have a specific
 * label (configured via "rca.label" property) and triggers RCA analysis for each
 * matching pod. This enables proactive monitoring and automatic issue detection
 * without manual intervention.
 * </p>
 * <p>
 * The scanner is triggered by {@link LifecycleService} on a scheduled interval
 * and works with pods from any workload type (Deployments, StatefulSets, DaemonSets)
 * as long as they have the configured label.
 * </p>
 * <p>
 * Example label configuration: "rca.enabled=true"
 * </p>
 *
 * @see LifecycleService
 * @see RcaOrchestrator
 */
@ApplicationScoped
public class WorkloadScannerService {

    private static final Logger LOG = Logger.getLogger(WorkloadScannerService.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    RcaOrchestrator rcaOrchestrator;

    /**
     * Label selector for identifying pods to analyze.
     * Format: "key=value" (e.g., "rca.enabled=true")
     */
    @ConfigProperty(name = "rca.label")
    String rcaLabel;

    /**
     * Scans all pods with the configured label and triggers RCA analysis.
     * <p>
     * This method:
     * <ol>
     *   <li>Parses the configured label selector</li>
     *   <li>Queries Kubernetes for all pods across namespaces with that label</li>
     *   <li>Triggers RCA analysis for each matching pod</li>
     *   <li>Logs the results and any errors encountered</li>
     * </ol>
     * </p>
     * <p>
     * The method is designed to be resilient - if analysis fails for one pod,
     * it continues processing the remaining pods.
     * </p>
     */
    public void scanWorkloads() {
        LOG.info("Starting scheduled workload scan for RCA...");

        String[] labelParts = rcaLabel.split("=");
        String labelKey = labelParts[0];
        String labelValue = labelParts.length > 1 ? labelParts[1] : "";

        // Find pods with the specified label. This covers pods that are part of
        // Deployments/StatefulSets/DaemonSets where the label is in the pod template.
        LOG.info("Searching for pods with label: " + rcaLabel);
        List<Pod> pods = kubernetesClient.pods().inAnyNamespace()
                .withLabel(labelKey, labelValue)
                .list()
                .getItems();

        if (pods.isEmpty()) {
            LOG.info("No pods found with label: " + rcaLabel);
            return;
        }

        LOG.info("Found " + pods.size() + " pods to analyze.");

        for (Pod pod : pods) {
            String namespace = pod.getMetadata().getNamespace();
            String podName = pod.getMetadata().getName();
            LOG.info(">>> Starting analysis for pod: " + namespace + "/" + podName);

            try {
                // Use the orchestrator to run the full analysis pipeline
                var report = rcaOrchestrator.runAnalysis(namespace, podName);
                LOG.info("<<< Analysis completed for pod: " + podName + ". Decision: " + report.issue);
            } catch (Exception e) {
                LOG.error("!!! Error analyzing pod " + podName, e);
            }
        }
    }
}
