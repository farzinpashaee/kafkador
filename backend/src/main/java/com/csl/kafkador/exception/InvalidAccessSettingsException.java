package com.csl.kafkador.exception;

/** Access settings that can't be saved, with a message fit to show on the setup page or in Settings. */
public class InvalidAccessSettingsException extends Exception {

    public InvalidAccessSettingsException(String message) {
        super(message);
    }

}
