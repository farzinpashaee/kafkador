package com.csl.kafkador.exception;

public class BrokerNotFoundException extends Exception {

    public BrokerNotFoundException(String message) {
        super(message);
    }

}
