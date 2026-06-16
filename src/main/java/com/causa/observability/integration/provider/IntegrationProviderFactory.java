package com.causa.observability.integration.provider;

import com.causa.common.logging.CausaLogger;
import com.causa.observability.integration.dto.ProviderType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating integration providers
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class IntegrationProviderFactory {

    private static final CausaLogger log = CausaLogger.getLogger(IntegrationProviderFactory.class);

    @Inject
    Instance<IntegrationProvider> providers;

    private final Map<String, IntegrationProvider> providerMap = new HashMap<>();

    /**
     * Initialize the provider map
     */
    public void init() {
        if (providerMap.isEmpty()) {
            for (IntegrationProvider provider : providers) {
                String providerType = provider.getProviderType().getValue();
                providerMap.put(providerType, provider);
                log.info("Registered integration provider")
                    .field("provider", providerType)
                    .log();
            }
        }
    }

    /**
     * Get a provider by type
     *
     * @param providerType the provider type string
     * @return the provider
     * @throws IllegalArgumentException if provider not found
     */
    public IntegrationProvider getProvider(String providerType) {
        init(); // Ensure providers are initialized
        
        IntegrationProvider provider = providerMap.get(providerType.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider type: " + providerType);
        }
        return provider;
    }

    /**
     * Get a provider by enum type
     *
     * @param providerType the provider type enum
     * @return the provider
     */
    public IntegrationProvider getProvider(ProviderType providerType) {
        return getProvider(providerType.getValue());
    }
}

// Made with Bob
