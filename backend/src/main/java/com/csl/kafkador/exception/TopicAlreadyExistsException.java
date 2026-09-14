package com.csl.kafkador.exception;

public class TopicAlreadyExistsException extends Exception {

    public TopicAlreadyExistsException(String message) {
        super(message);
    }

}
