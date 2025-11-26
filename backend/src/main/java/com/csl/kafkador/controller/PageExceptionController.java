package com.csl.kafkador.controller;

import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(assignableTypes = PageController.class)
public class PageExceptionController {

    @ExceptionHandler(ConnectionSessionExpiredException.class)
    public RedirectView handleConnectionSessionExpiredException(ConnectionSessionExpiredException ex, HttpServletRequest request , HttpSession session) {
        String requestedUri = request.getRequestURI();
        if (request.getQueryString() != null) {
            requestedUri += "?" + request.getQueryString();
        }
        session.setAttribute("redirectAfterLogin", requestedUri);
        return new RedirectView(ex.getRedirectUrl());
    }

}
