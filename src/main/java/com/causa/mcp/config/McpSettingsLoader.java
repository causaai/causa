package com.causa.mcp.config;

import com.causa.common.exceptions.McpConfigLoadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP Settings Loader
 *
 * <p>Reads and validates whatever file {@code causa.mcp.config-file} points to — the loader never
 * hardcodes which of the deployment-mode JSONs (cluster/developer/vm) that is; that choice is made
 * entirely by {@code application.yml}/{@code MCP_CONFIG_FILE}.
 *
 * <p>Uses the existing Jackson {@link ObjectMapper} and Jakarta {@link Validator} CDI beans — no
 * hand-written parsing or manual null/blank checks.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpSettingsLoader {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String configFile;

    @Inject
    public McpSettingsLoader(
            ObjectMapper objectMapper,
            Validator validator,
            @ConfigProperty(name = "causa.mcp.config-file") String configFile) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.configFile = configFile;
    }

    /**
     * Loads and validates the MCP settings file.
     *
     * @return the parsed, validated {@link McpSettings}
     * @throws McpConfigLoadException if the file is missing/unreadable, the JSON is invalid, or
     *                                 Bean Validation fails
     */
    public McpSettings load() {
        McpSettings settings;
        try {
            settings = objectMapper.readValue(new File(configFile), McpSettings.class);
        } catch (IOException e) {
            throw new McpConfigLoadException(
                    "Failed to read MCP config file at " + configFile, "IOException", e);
        }

        if (settings == null) {
            throw new McpConfigLoadException(
                    "MCP config at " + configFile + " is empty or null", "ValidationFailed");
        }

        Set<ConstraintViolation<McpSettings>> violations = validator.validate(settings);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new McpConfigLoadException(
                    "MCP config at " + configFile + " failed validation: " + details, "ValidationFailed");
        }

        return settings;
    }
}
