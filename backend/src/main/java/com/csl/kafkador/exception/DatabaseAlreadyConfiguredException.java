package com.csl.kafkador.exception;

public class DatabaseAlreadyConfiguredException extends Exception {

    public DatabaseAlreadyConfiguredException(String message) {
        super(message);
    }

}
