package com.causa.mcp.config;

import com.causa.common.exceptions.McpConfigLoadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpSettingsLoader}.
 *
 * @since 0.0.1
 */
@DisplayName("McpSettingsLoader Tests")
class McpSettingsLoaderTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static final String VALID_JSON = """
            {
              "mcpServers": {
                "kubernetes": {
                  "type": "streamable-http",
                  "url": "http://kubernetes-mcp-server:8080/mcp",
                  "headers": {},
                  "optional": false,
                  "alwaysAllow": ["pods_get", "pods_log"],
                  "healthCheck": { "url": "http://kubernetes-mcp-server:8080/healthz", "timeoutMs": 5000 },
                  "timeoutMs": 5000,
                  "description": "Kubernetes MCP",
                  "tools": [
                    { "name": "pods_get", "contextKey": "POD_STATUS", "arguments": { "name": "${podName}" } }
                  ]
                }
              }
            }
            """;

    private McpSettingsLoader loaderFor(String configPath) {
        return new McpSettingsLoader(objectMapper, validator, configPath);
    }

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("Loads and validates a well-formed mcp.json")
    void loadsValidFile() throws IOException {
        Path file = writeFile("mcp.json", VALID_JSON);

        McpSettings settings = loaderFor(file.toString()).load();

        assertEquals(1, settings.mcpServers().size());
        McpSettings.ServerConfig kubernetes = settings.mcpServers().get("kubernetes");
        assertNotNull(kubernetes);
        assertEquals("http://kubernetes-mcp-server:8080/mcp", kubernetes.url());
        assertFalse(kubernetes.optional());
        assertEquals(5000, kubernetes.healthCheck().timeoutMs());
        assertEquals(1, kubernetes.tools().size());
        assertEquals("POD_STATUS", kubernetes.tools().get(0).contextKey());
    }

    @Test
    @DisplayName("Throws McpConfigLoadException when the file is missing")
    void throwsWhenFileMissing() {
        String missingPath = tempDir.resolve("does-not-exist.json").toString();

        McpConfigLoadException ex = assertThrows(McpConfigLoadException.class,
                () -> loaderFor(missingPath).load());
        assertEquals("IOException", ex.getErrorType());
    }

    @Test
    @DisplayName("Throws McpConfigLoadException when the JSON is malformed")
    void throwsWhenJsonMalformed() throws IOException {
        Path file = writeFile("broken.json", "{ not valid json ");

        McpConfigLoadException ex = assertThrows(McpConfigLoadException.class,
                () -> loaderFor(file.toString()).load());
        assertEquals("IOException", ex.getErrorType());
    }

    @Test
    @DisplayName("Throws McpConfigLoadException when the file content is the JSON literal null")
    void throwsWhenJsonIsNullLiteral() throws IOException {
        Path file = writeFile("null.json", "null");

        McpConfigLoadException ex = assertThrows(McpConfigLoadException.class,
                () -> loaderFor(file.toString()).load());
        assertEquals("ValidationFailed", ex.getErrorType());
    }

    @Test
    @DisplayName("Throws McpConfigLoadException when mcpServers is empty")
    void throwsWhenMcpServersEmpty() throws IOException {
        Path file = writeFile("empty.json", "{ \"mcpServers\": {} }");

        McpConfigLoadException ex = assertThrows(McpConfigLoadException.class,
                () -> loaderFor(file.toString()).load());
        assertEquals("ValidationFailed", ex.getErrorType());
        assertTrue(ex.getMessage().contains("mcpServers"));
    }

    @Test
    @DisplayName("Throws McpConfigLoadException when a required field is blank")
    void throwsWhenRequiredFieldBlank() throws IOException {
        String json = """
                {
                  "mcpServers": {
                    "kubernetes": {
                      "type": "streamable-http",
                      "url": "",
                      "optional": false,
                      "alwaysAllow": ["pods_get"],
                      "healthCheck": { "url": "http://k8s:8080/healthz", "timeoutMs": 5000 }
                    }
                  }
                }
                """;
        Path file = writeFile("blank-url.json", json);

        McpConfigLoadException ex = assertThrows(McpConfigLoadException.class,
                () -> loaderFor(file.toString()).load());
        assertEquals("ValidationFailed", ex.getErrorType());
    }
}