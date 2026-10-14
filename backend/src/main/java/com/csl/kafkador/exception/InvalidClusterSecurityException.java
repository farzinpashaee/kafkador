package com.csl.kafkador.exception;

/** Security settings that can't be used as given, e.g. a truststore file that doesn't open with its password. */
public class InvalidClusterSecurityException extends Exception {

    public InvalidClusterSecurityException(String message) {
        super(message);
    }

}
