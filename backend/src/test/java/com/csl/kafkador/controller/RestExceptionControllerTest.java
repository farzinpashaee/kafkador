package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.DuplicatedClusterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionControllerTest {

    private RestExceptionController controller;

    @BeforeEach
    void setUp() {
        controller = new RestExceptionController();
    }

    @Test
    void sessionExpired_returns401() {
        ConnectionSessionExpiredException ex =
                new ConnectionSessionExpiredException("Session expired", "/connect");

        ResponseEntity<GenericResponse<Void>> response =
                controller.handleConnectionSessionExpiredException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode())
                .isEqualTo(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void duplicatedCluster_returns400() {
        DuplicatedClusterException ex = new DuplicatedClusterException("Cluster already exists");

        ResponseEntity<GenericResponse<Void>> response =
                controller.handleDuplicatedClusterException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode())
                .isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Cluster already exists");
    }

    @Test
    void configurationRequired_returns428() throws Exception {
        ConfigurationRequiredException ex =
                new ConfigurationRequiredException("APM agent configuration required");

        ResponseEntity<GenericResponse<Void>> response =
                controller.handleConfigurationRequired(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody().getError().getCode())
                .isEqualTo(String.valueOf(HttpStatus.PRECONDITION_REQUIRED.value()));
    }

    @Test
    void unhandledException_returns500() {
        RuntimeException ex = new RuntimeException("Unexpected failure");

        ResponseEntity<GenericResponse<Void>> response =
                controller.anyOtherException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getCode())
                .isEqualTo(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @Test
    void errorResponse_doesNotExposeRawExceptionMessage() {
        // Raw exception messages (stack traces, class names, SQL) must not reach clients.
        // The catch-all handler should return a generic message, not ex.getMessage().
        RuntimeException ex = new RuntimeException("internal DB detail: table CLUSTER_CONFIG row 42");

        ResponseEntity<GenericResponse<Void>> response =
                controller.anyOtherException(ex);

        String returnedMessage = response.getBody().getError().getMessage();
        assertThat(returnedMessage)
                .as("Raw exception message must not be forwarded to the client")
                .doesNotContain("DB detail")
                .doesNotContain("table CLUSTER_CONFIG");
    }
}
