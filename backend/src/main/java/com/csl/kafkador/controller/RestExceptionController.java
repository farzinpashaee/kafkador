package com.csl.kafkador.controller;

import com.csl.kafkador.domain.ErrorResponse;
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
    public ResponseEntity<ErrorResponse> handleConnectionSessionExpiredException(ConnectionSessionExpiredException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(HttpStatus.UNAUTHORIZED.value());
        error.setMessage(ex.getMessage());
        error.setTimestamp(System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DuplicatedClusterException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatedClusterException(DuplicatedClusterException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setMessage(ex.getMessage());
        error.setTimestamp(System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConfigurationRequiredException.class)
    public ResponseEntity<Object> handleConfigurationRequired(ConfigurationRequiredException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
        error.setMessage(ex.getMessage());
        error.setTimestamp(System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.PRECONDITION_REQUIRED);
    }

}
