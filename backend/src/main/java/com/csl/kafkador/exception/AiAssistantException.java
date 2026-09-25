package com.csl.kafkador.exception;

/** The message is always safe to show to the client (never contains keys or internal details). */
public class AiAssistantException extends Exception {

    public AiAssistantException(String message) {
        super(message);
    }

}
