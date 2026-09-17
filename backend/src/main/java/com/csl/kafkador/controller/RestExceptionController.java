package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

/**
 * Centralized error mapping for the REST API. Every handler logs the full exception
 * server-side; only handlers explicitly built for a known, safe-to-disclose exception
 * type echo the exception message back to the client. Anything unexpected is reduced
 * to a generic message so internal details (hosts, stack traces, library errors)
 * never leak into an API response.
 */
@ControllerAdvice(assignableTypes = ApiController.class)
@Slf4j
public class RestExceptionController {

    @ExceptionHandler(ConnectionSessionExpiredException.class)
    public ResponseEntity<GenericResponse<Void>> handleConnectionSessionExpiredException(ConnectionSessionExpiredException ex) {
        log.debug("Session expired: {}", ex.getMessage());
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.UNAUTHORIZED.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DuplicatedClusterException.class)
    public ResponseEntity<GenericResponse<Void>> handleDuplicatedClusterException(DuplicatedClusterException ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConfigurationRequiredException.class)
    public ResponseEntity<GenericResponse<Void>> handleConfigurationRequired(ConfigurationRequiredException ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.PRECONDITION_REQUIRED.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.PRECONDITION_REQUIRED);
    }

    @ExceptionHandler(AuthorizerNotConfiguredException.class)
    public ResponseEntity<GenericResponse<Void>> handleAuthorizerNotConfigured(AuthorizerNotConfiguredException ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.PRECONDITION_REQUIRED.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.PRECONDITION_REQUIRED);
    }

    @ExceptionHandler({ClusterNotFoundException.class, BrokerNotFoundException.class,
            AlertNotFoundException.class, ConfigNotFoundException.class, TopicNotFoundException.class})
    public ResponseEntity<GenericResponse<Void>> handleNotFound(Exception ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TopicAlreadyExistsException.class)
    public ResponseEntity<GenericResponse<Void>> handleTopicAlreadyExists(TopicAlreadyExistsException ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.CONFLICT.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .message(message.isBlank() ? "Invalid request" : message)
                .failed(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(KafkaAdminApiException.class)
    public ResponseEntity<GenericResponse<Void>> handleKafkaAdminApiException(KafkaAdminApiException ex) {
        log.error("Kafka admin API call failed", ex);
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.BAD_GATEWAY.value()))
                .message("The Kafka cluster could not complete this request. Please try again.")
                .failed(HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(KsqlDbApiException.class)
    public ResponseEntity<GenericResponse<Void>> handleKsqlDbApiException(KsqlDbApiException ex) {
        log.error("ksqlDB API call failed", ex);
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.BAD_GATEWAY.value()))
                .message("The ksqlDB server could not complete this request. Please try again.")
                .failed(HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Void>> anyOtherException(Exception ex) {
        log.error("Unhandled exception in API request", ex);
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .message("An unexpected error occurred. Please try again or contact support.")
                .failed(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
