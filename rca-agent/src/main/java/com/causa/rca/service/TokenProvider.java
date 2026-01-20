package com.causa.rca.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Service for providing Kubernetes service account authentication tokens.
 * <p>
 * This service reads the service account token from the standard Kubernetes
 * mounted secret location and provides it in the format required for Bearer
 * authentication with Kubernetes API and other services (Prometheus, Cryostat).
 * </p>
 * <p>
 * The token is cached after the first read to avoid repeated file I/O operations.
 * If the token file is not found (e.g., running outside Kubernetes), an empty
 * token is returned to allow graceful degradation.
 * </p>
 *
 * @see DataCollectorService
 * @see PrometheusClient
 * @see CryostatClient
 */
@ApplicationScoped
public class TokenProvider {

    private static final Logger LOG = Logger.getLogger(TokenProvider.class);
    
    /**
     * Standard path where Kubernetes mounts the service account token.
     */
    private static final String TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * Cached token value to avoid repeated file reads.
     */
    private String token;

    /**
     * Retrieves the Kubernetes service account token for authentication.
     * <p>
     * On first call, reads the token from the Kubernetes service account secret
     * mount point and caches it. Subsequent calls return the cached value.
     * The token is formatted with "Bearer " prefix for HTTP Authorization headers.
     * </p>
     * <p>
     * If the token file doesn't exist (e.g., when running locally outside Kubernetes),
     * returns an empty string to allow the application to continue with limited
     * functionality.
     * </p>
     *
     * @return the service account token in "Bearer {token}" format, or empty string
     *         if the token cannot be read
     */
    public String getToken() {
        if (token == null) {
            try {
                if (Files.exists(Paths.get(TOKEN_PATH))) {
                    token = "Bearer " + Files.readString(Paths.get(TOKEN_PATH)).trim();
                    LOG.info("Service account token loaded successfully.");
                } else {
                    LOG.warn("Service account token file not found at " + TOKEN_PATH + ". Using empty token.");
                    token = "";
                }
            } catch (IOException e) {
                LOG.error("Failed to read service account token", e);
                token = "";
            }
        }
        return token;
    }
}
