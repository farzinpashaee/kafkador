package com.csl.kafkador.exception;

/** A consumer group offset reset that can't be done as asked, e.g. while the group still has active members. */
public class InvalidOffsetResetException extends Exception {

    public InvalidOffsetResetException(String message) {
        super(message);
    }

}
