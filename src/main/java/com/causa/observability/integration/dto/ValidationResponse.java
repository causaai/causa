package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for credential validation
 *
 * @since 1.0.0
 */
public class ValidationResponse {

    @JsonProperty("valid")
    private boolean valid;

    @JsonProperty("message")
    private String message;

    @JsonProperty("permissions")
    private List<String> permissions = new ArrayList<>();

    @JsonProperty("missingPermissions")
    private List<String> missingPermissions = new ArrayList<>();

    @JsonProperty("error")
    private String error;

    public ValidationResponse() {
    }

    public ValidationResponse(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<String> getMissingPermissions() {
        return missingPermissions;
    }

    public void setMissingPermissions(List<String> missingPermissions) {
        this.missingPermissions = missingPermissions;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

// Made with Bob
