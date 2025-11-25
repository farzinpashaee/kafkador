package com.csl.kafkador.controller;

import com.csl.kafkador.domain.ErrorResponse;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
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

}
