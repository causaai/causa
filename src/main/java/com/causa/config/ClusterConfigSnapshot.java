package com.causa.config;

import java.util.Map;

/**
 * Cluster Configuration Snapshot
 *
 * <p>Immutable typed view of cluster-related configuration from the runtime cache.
 * Reads 2 cluster config keys from AppConfig and provides typed getters with sensible defaults.
 *
 * @since 0.0.1
 */
public final class ClusterConfigSnapshot {

    private final Map<String, String> config;

    public ClusterConfigSnapshot(Map<String, String> config) {
        this.config = Map.copyOf(config);
    }

    public String getClusterName() {
        return config.getOrDefault("CLUSTER_NAME", "default");
    }

    public String getClusterType() {
        return config.getOrDefault("CLUSTER_TYPE", "vm");
    }
}
