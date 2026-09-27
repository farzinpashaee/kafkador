package com.csl.kafkador.exception;

/** Message is written to be safe to show to the client (no SQL text, file paths, or credentials). */
public class DatabaseSetupException extends Exception {

    public DatabaseSetupException(String message, Throwable cause) {
        super(message, cause);
    }

}
