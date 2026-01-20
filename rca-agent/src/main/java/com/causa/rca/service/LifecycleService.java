package com.causa.rca.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service managing application lifecycle events and scheduled tasks.
 * <p>
 * This service handles:
 * <ul>
 *   <li>Application startup initialization</li>
 *   <li>RAG (Retrieval-Augmented Generation) document ingestion on startup</li>
 *   <li>Scheduled workload scanning for automatic RCA analysis</li>
 * </ul>
 * </p>
 * <p>
 * The workload scanner runs periodically based on the configured interval
 * (rca.scan.interval property) to automatically detect and analyze pods
 * with issues.
 * </p>
 *
 * @see RagService
 * @see WorkloadScannerService
 */
@ApplicationScoped
public class LifecycleService {

    private static final Logger LOG = Logger.getLogger(LifecycleService.class);

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @Inject
    RagService ragService;

    @Inject
    WorkloadScannerService workloadScannerService;

    /**
     * Scheduled task that periodically scans workloads for RCA analysis.
     * <p>
     * Runs at the interval specified by the "rca.scan.interval" configuration property,
     * with an initial delay of 10 seconds after application startup. This allows the
     * system to automatically monitor and analyze pods with specific labels.
     * </p>
     */
    @Scheduled(every = "${rca.scan.interval}", delayed = "10s")
    public void scanWorkloads() {
        LOG.debug("Scheduled workload scan triggered."); // Changed to debug for less verbosity
        workloadScannerService.scanWorkloads();
    }

    /**
     * Handles application startup event.
     * <p>
     * Performs initialization tasks including:
     * <ul>
     *   <li>Ingesting RAG knowledge base documents if RAG is enabled</li>
     *   <li>Initializing the scheduled workload scanner</li>
     *   <li>Logging configuration status</li>
     * </ul>
     * </p>
     *
     * @param ev the startup event (automatically provided by Quarkus)
     */
    void onStart(@Observes StartupEvent ev) {
        LOG.info("=== RCA Agent Lifecycle Started ===");

        if (ragEnabled) {
            LOG.info("RAG feature is ENABLED in config. Triggering ingestion...");
            ragService.ingestDocuments();
        } else {
            LOG.info("RAG feature is DISABLED in config.");
        }

        LOG.info("Scheduled scanner initialized with 10s delay.");
        LOG.info("=== RCA Agent Services Initialized ===");
    }
}
