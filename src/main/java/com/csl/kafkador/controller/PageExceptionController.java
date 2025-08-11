package com.csl.kafkador.controller;

import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(assignableTypes = PageController.class)
public class PageExceptionController {

    @ExceptionHandler(ConnectionSessionExpiredException.class)
    public RedirectView handleConnectionSessionExpiredException(ConnectionSessionExpiredException ex) {
        return new RedirectView(ex.getRedirectUrl());
    }

}
