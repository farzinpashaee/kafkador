package com.csl.kafkador.exception;

/** A Records tab read or produce request that can't be served as asked, e.g. a partition the topic doesn't have. */
public class InvalidRecordRequestException extends Exception {

    public InvalidRecordRequestException(String message) {
        super(message);
    }

}
