package com.csl.kafkador.exception;

public class ConsumerGroupNotFoundException extends Exception {

    public ConsumerGroupNotFoundException(String message) {
        super(message);
    }

}
