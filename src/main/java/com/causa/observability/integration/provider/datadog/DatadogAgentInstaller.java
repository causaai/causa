package com.causa.observability.integration.provider.datadog;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.causa.common.logging.CausaLogger;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Handles Datadog Agent installation in Kubernetes/OpenShift cluster
 */
@ApplicationScoped
public class DatadogAgentInstaller {

    private static final CausaLogger log = CausaLogger.getLogger(DatadogAgentInstaller.class);
    
    private static final String DATADOG_NAMESPACE = "openshift-operators";
    private static final String DATADOG_SECRET_NAME = "datadog-secret";
    private static final String DATADOG_AGENT_NAME = "datadog";
    
    @Inject
    KubernetesClient kubernetesClient;

    /**
     * Check if Datadog Agent is already deployed
     */
    public boolean isDatadogAgentDeployed() {
        try {
            // Check if DatadogAgent CR exists
            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("datadoghq.com")
                    .withVersion("v2alpha1")
                    .withKind("DatadogAgent")
                    .withPlural("datadogagents")
                    .withScope("Namespaced")
                    .build();

            var agent = kubernetesClient.genericKubernetesResources(crdContext)
                    .inNamespace(DATADOG_NAMESPACE)
                    .withName(DATADOG_AGENT_NAME)
                    .get();

            boolean exists = agent != null;
            
            log.info("Checked for existing Datadog Agent")
                    .field("exists", exists)
                    .field("namespace", DATADOG_NAMESPACE)
                    .log();
            
            return exists;
            
        } catch (Exception e) {
            log.warn("Could not check for existing Datadog Agent")
                    .field("error", e.getMessage())
                    .log();
            return false;
        }
    }

    /**
     * Install Datadog Agent in the cluster
     */
    public InstallationResult installAgent(String apiKey, String appKey, String site, String clusterName) {
        InstallationResult result = new InstallationResult();
        
        try {
            log.info("Starting Datadog Agent installation")
                    .field("namespace", DATADOG_NAMESPACE)
                    .field("site", site)
                    .field("clusterName", clusterName)
                    .log();

            // Step 1: Check if agent already exists
            if (isDatadogAgentDeployed()) {
                log.info("Datadog Agent already deployed, skipping installation").log();
                result.setSuccess(true);
                result.setAlreadyExists(true);
                result.setMessage("Datadog Agent already deployed, reusing existing installation");
                return result;
            }

            // Step 2: Create namespace if it doesn't exist
            ensureNamespaceExists(DATADOG_NAMESPACE);

            // Step 3: Create or update secret with credentials
            createOrUpdateSecret(apiKey, appKey);

            // Step 4: Deploy DatadogAgent CR
            deployDatadogAgent(site, clusterName);

            result.setSuccess(true);
            result.setAlreadyExists(false);
            result.setMessage("Datadog Agent installed successfully");
            
            log.info("Datadog Agent installation completed successfully").log();
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Failed to install Datadog Agent: " + e.getMessage());
            result.setError(e.getMessage());
            
            log.error("Datadog Agent installation failed")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }
        
        return result;
    }

    /**
     * Uninstall Datadog Agent from the cluster
     */
    public boolean uninstallAgent() {
        try {
            log.info("Uninstalling Datadog Agent")
                    .field("namespace", DATADOG_NAMESPACE)
                    .log();

            // Delete DatadogAgent CR
            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("datadoghq.com")
                    .withVersion("v2alpha1")
                    .withKind("DatadogAgent")
                    .withPlural("datadogagents")
                    .withScope("Namespaced")
                    .build();

            kubernetesClient.genericKubernetesResources(crdContext)
                    .inNamespace(DATADOG_NAMESPACE)
                    .withName(DATADOG_AGENT_NAME)
                    .delete();

            // Delete secret
            kubernetesClient.secrets()
                    .inNamespace(DATADOG_NAMESPACE)
                    .withName(DATADOG_SECRET_NAME)
                    .delete();

            log.info("Datadog Agent uninstalled successfully").log();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to uninstall Datadog Agent")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
            return false;
        }
    }

    /**
     * Get Datadog Agent status
     */
    public AgentStatus getAgentStatus() {
        AgentStatus status = new AgentStatus();
        
        try {
            // Check if DatadogAgent CR exists
            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("datadoghq.com")
                    .withVersion("v2alpha1")
                    .withKind("DatadogAgent")
                    .withPlural("datadogagents")
                    .withScope("Namespaced")
                    .build();

            var agent = kubernetesClient.genericKubernetesResources(crdContext)
                    .inNamespace(DATADOG_NAMESPACE)
                    .withName(DATADOG_AGENT_NAME)
                    .get();

            if (agent == null) {
                status.setInstalled(false);
                status.setHealthy(false);
                status.setMessage("Datadog Agent not installed");
                return status;
            }

            status.setInstalled(true);

            // Check agent pods
            PodList pods = kubernetesClient.pods()
                    .inNamespace(DATADOG_NAMESPACE)
                    .withLabel("agent.datadoghq.com/component", "agent")
                    .list();

            int runningPods = 0;
            int totalPods = pods.getItems().size();

            for (Pod pod : pods.getItems()) {
                if ("Running".equals(pod.getStatus().getPhase())) {
                    runningPods++;
                }
            }

            status.setHealthy(runningPods > 0);
            status.setRunningPods(runningPods);
            status.setTotalPods(totalPods);
            status.setMessage(String.format("Agent pods: %d/%d running", runningPods, totalPods));

            log.info("Agent status retrieved")
                    .field("installed", status.isInstalled())
                    .field("healthy", status.isHealthy())
                    .field("runningPods", runningPods)
                    .field("totalPods", totalPods)
                    .log();
            
        } catch (Exception e) {
            status.setInstalled(false);
            status.setHealthy(false);
            status.setMessage("Error checking agent status: " + e.getMessage());
            
            log.error("Failed to get agent status")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }
        
        return status;
    }

    /**
     * Ensure namespace exists
     */
    private void ensureNamespaceExists(String namespace) {
        Namespace ns = kubernetesClient.namespaces().withName(namespace).get();
        
        if (ns == null) {
            log.info("Creating namespace")
                    .field("namespace", namespace)
                    .log();
            
            kubernetesClient.namespaces().create(new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .endMetadata()
                    .build());
        } else {
            log.info("Namespace already exists")
                    .field("namespace", namespace)
                    .log();
        }
    }

    /**
     * Create or update secret with Datadog credentials
     */
    private void createOrUpdateSecret(String apiKey, String appKey) {
        log.info("Creating/updating Datadog secret")
                .field("secretName", DATADOG_SECRET_NAME)
                .field("namespace", DATADOG_NAMESPACE)
                .log();

        Map<String, String> data = new HashMap<>();
        data.put("api-key", apiKey);
        data.put("app-key", appKey);

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withName(DATADOG_SECRET_NAME)
                .withNamespace(DATADOG_NAMESPACE)
                .endMetadata()
                .withStringData(data)
                .build();

        kubernetesClient.secrets()
                .inNamespace(DATADOG_NAMESPACE)
                .createOrReplace(secret);

        log.info("Datadog secret created/updated successfully").log();
    }

    /**
     * Deploy DatadogAgent CR
     */
    private void deployDatadogAgent(String site, String clusterName) {
        try {
            log.info("Deploying DatadogAgent CR")
                    .field("site", site)
                    .field("clusterName", clusterName)
                    .log();

            // Build the DatadogAgent YAML
            String agentYaml = buildDatadogAgentYaml(site, clusterName);

            // Apply the CR
            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("datadoghq.com")
                    .withVersion("v2alpha1")
                    .withKind("DatadogAgent")
                    .withPlural("datadogagents")
                    .withScope("Namespaced")
                    .build();

            kubernetesClient.genericKubernetesResources(crdContext)
                    .inNamespace(DATADOG_NAMESPACE)
                    .load(new java.io.ByteArrayInputStream(agentYaml.getBytes(StandardCharsets.UTF_8)))
                    .createOrReplace();

            log.info("DatadogAgent CR deployed successfully").log();
            
        } catch (Exception e) {
            log.error("Failed to deploy DatadogAgent CR")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
            throw new RuntimeException("Failed to deploy DatadogAgent CR", e);
        }
    }

    /**
     * Build DatadogAgent YAML with dynamic values
     */
    private String buildDatadogAgentYaml(String site, String clusterName) {
        return String.format("""
kind: DatadogAgent
apiVersion: datadoghq.com/v2alpha1

metadata:
  name: %s
  namespace: %s

spec:
  global:
    site: %s
    clusterName: %s

    credentials:
      apiSecret:
        secretName: %s
        keyName: api-key

    kubelet:
      tlsVerify: false

  features:
    prometheusScrape:
      enabled: true
      serviceEndpoints: true

    logCollection:
      enabled: false

    liveProcessCollection:
      enabled: false

    orchestratorExplorer:
      enabled: false

    apm:
      enabled: false

  override:
    nodeAgent:
      hostNetwork: true

      env:
        - name: DD_HOSTNAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName

      tolerations:
        - operator: Exists
""", DATADOG_AGENT_NAME, DATADOG_NAMESPACE, site, clusterName, DATADOG_SECRET_NAME);
    }

    /**
     * Installation result
     */
    public static class InstallationResult {
        private boolean success;
        private boolean alreadyExists;
        private String message;
        private String error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public boolean isAlreadyExists() { return alreadyExists; }
        public void setAlreadyExists(boolean alreadyExists) { this.alreadyExists = alreadyExists; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /**
     * Agent status
     */
    public static class AgentStatus {
        private boolean installed;
        private boolean healthy;
        private int runningPods;
        private int totalPods;
        private String message;

        public boolean isInstalled() { return installed; }
        public void setInstalled(boolean installed) { this.installed = installed; }

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public int getRunningPods() { return runningPods; }
        public void setRunningPods(int runningPods) { this.runningPods = runningPods; }

        public int getTotalPods() { return totalPods; }
        public void setTotalPods(int totalPods) { this.totalPods = totalPods; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

// Made with Bob
