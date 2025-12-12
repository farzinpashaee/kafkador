package com.csl.kafkador.exception;

public class ConnectionSessionExpiredException extends RuntimeException {
    private String redirectUrl;

    public ConnectionSessionExpiredException(String message, String redirectUrl) {
        super(message);
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}
