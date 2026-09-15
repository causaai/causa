package com.causa.common.exceptions;

import com.causa.api.dto.response.ErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GlobalExceptionMapper Tests")
class GlobalExceptionMapperTest {

    GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() { mapper = new GlobalExceptionMapper(); }

    @Nested @DisplayName("handleAlertException Tests")
    class AlertTests {
        @Test void returns500() {
            Response r = mapper.handleAlertException(new AlertException("bad alert", "TYPE"));
            assertThat(r.getStatus()).isEqualTo(500);
            assertThat(r.getEntity()).isInstanceOf(ErrorResponse.class);
            assertThat(((ErrorResponse) r.getEntity()).message()).contains("bad alert");
        }
    }

    @Nested @DisplayName("handleDiagnosticException Tests")
    class DiagnosticTests {
        @Test void returns500() {
            Response r = mapper.handleDiagnosticException(new DiagnosticException("diag fail", "T"));
            assertThat(r.getStatus()).isEqualTo(500);
            assertThat(((ErrorResponse) r.getEntity()).message()).contains("diag fail");
        }
    }

    @Nested @DisplayName("handleNotFoundException Tests")
    class NotFoundTests {
        @Test void returns404() {
            Response r = mapper.handleNotFoundException(new NotFoundException("gone"));
            assertThat(r.getStatus()).isEqualTo(404);
            assertThat(((ErrorResponse) r.getEntity()).error()).isEqualTo("Not Found");
        }
    }

    @Nested @DisplayName("handleMethodNotAllowedException Tests")
    class MethodNotAllowedTests {
        @Test void returns405() {
            Response r = mapper.handleMethodNotAllowedException(new NotAllowedException("POST"));
            assertThat(r.getStatus()).isEqualTo(405);
            assertThat(((ErrorResponse) r.getEntity()).error()).isEqualTo("Method Not Allowed");
        }
    }

    @Nested @DisplayName("handleBadRequestException Tests")
    class BadRequestTests {
        @Test void returns400WithMessage() {
            Response r = mapper.handleBadRequestException(new BadRequestException("bad input"));
            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(((ErrorResponse) r.getEntity()).message()).contains("bad input");
        }
        @Test void returns400WithNullMessage() {
            Response r = mapper.handleBadRequestException(new BadRequestException());
            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(((ErrorResponse) r.getEntity()).message()).isNotBlank();
        }
    }

    @Nested @DisplayName("handleWebApplicationException Tests")
    class WebAppTests {
        @Test void returns401ForClientError() {
            Response r = mapper.handleWebApplicationException(new WebApplicationException(401));
            assertThat(r.getStatus()).isEqualTo(401);
        }
        @Test void returns503ForServerError() {
            Response r = mapper.handleWebApplicationException(new WebApplicationException(503));
            assertThat(r.getStatus()).isEqualTo(503);
        }
        @Test void handles500WithMessage() {
            Response r = mapper.handleWebApplicationException(new WebApplicationException("server error", 500));
            assertThat(r.getStatus()).isEqualTo(500);
        }
    }

    @Nested @DisplayName("handleException Tests")
    class GenericExceptionTests {
        @Test void returns500ForAnyException() {
            Response r = mapper.handleException(new RuntimeException("boom"));
            assertThat(r.getStatus()).isEqualTo(500);
            assertThat(((ErrorResponse) r.getEntity()).error()).isEqualTo("Internal Server Error");
        }
        @Test void handlesNullMessage() {
            Response r = mapper.handleException(new RuntimeException());
            assertThat(r.getStatus()).isEqualTo(500);
        }
    }
}
