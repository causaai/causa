package com.causa.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMessages Constants Tests")
class LogMessagesTest {

    @Test void globalMessages_notBlank() {
        assertThat(LogMessages.UNEXPECTED_ERROR).isNotBlank();
        assertThat(LogMessages.APP_STARTED).isNotBlank();
    }

    @Test void healthMessages_notBlank() {
        assertThat(LogMessages.Health.LIVENESS_CHECK_CALLED).isNotBlank();
        assertThat(LogMessages.Health.READINESS_CHECK_PASSED).isNotBlank();
        assertThat(LogMessages.Health.READINESS_CHECK_FAILED).isNotBlank();
        assertThat(LogMessages.Health.LLM_READINESS_PASSED).isNotBlank();
        assertThat(LogMessages.Health.LLM_READINESS_FAILED).isNotBlank();
    }

    @Test void llmMessages_notBlank() {
        assertThat(LogMessages.LLM.LLM_FACTORY_INITIALIZING).isNotBlank();
        assertThat(LogMessages.LLM.PROMPT_SEND_START).isNotBlank();
        assertThat(LogMessages.LLM.PROMPT_SEND_SUCCESS).isNotBlank();
        assertThat(LogMessages.LLM.MODEL_NOT_AVAILABLE).isNotBlank();
        assertThat(LogMessages.LLM.BOB_NOT_AVAILABLE).isNotBlank();
        assertThat(LogMessages.LLM.BOB_EMPTY_RESPONSE).isNotBlank();
    }

    @Test void databaseMessages_notBlank() {
        assertThat(LogMessages.Database.CONNECTION_VERIFYING).isNotBlank();
        assertThat(LogMessages.Database.CONNECTION_SUCCESS).isNotBlank();
        assertThat(LogMessages.Database.CONNECTION_FAILED).isNotBlank();
    }

    @Test void alertMessages_notBlank() {
        assertThat(LogMessages.Alert.WEBHOOK_RECEIVED).isNotBlank();
        assertThat(LogMessages.Alert.ALERT_ACCEPTED).isNotBlank();
        assertThat(LogMessages.Alert.ALERT_PROCESSING_ERROR).isNotBlank();
    }

    @Test void diagnosticMessages_notBlank() {
        assertThat(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED).isNotBlank();
        assertThat(LogMessages.Diagnostic.DIAGNOSTIC_COMPLETED).isNotBlank();
        assertThat(LogMessages.Diagnostic.DIAGNOSTIC_FAILED).isNotBlank();
    }

    @Test void mcpMessages_notBlank() {
        assertThat(LogMessages.Mcp.MCP_CONTEXT_COLLECTION_START).isNotBlank();
        assertThat(LogMessages.Mcp.MCP_CALL_FAILED).isNotBlank();
    }

    @Test void skillsMessages_notBlank() {
        assertThat(LogMessages.Skills.SKILLS_DISABLED).isNotBlank();
        assertThat(LogMessages.Skills.SKILLS_MERGED).isNotBlank();
    }
}
