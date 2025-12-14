package com.csl.kafkador.controller;

import com.csl.kafkador.domain.ErrorResponse;
import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.DuplicatedClusterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = ApiController.class)
public class RestExceptionController {

    @ExceptionHandler(ConnectionSessionExpiredException.class)
    public ResponseEntity<GenericResponse<Void>> handleConnectionSessionExpiredException(ConnectionSessionExpiredException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(HttpStatus.UNAUTHORIZED.value());
        error.setMessage(ex.getMessage());
        error.setTimestamp(System.currentTimeMillis());
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Void>> anyOtherException(Exception ex) {
        return new GenericResponse.Builder<Void>()
                .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .message(ex.getMessage())
                .failed(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
