package com.csl.kafkador.exception;

/** First-run setup was asked for without the one-time setup token, or with a wrong one. */
public class SetupTokenException extends Exception {

    public SetupTokenException(String message) {
        super(message);
    }

}
