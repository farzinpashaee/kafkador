package com.csl.kafkador.exception;

/** ksqlDB rejected a statement, or it isn't one the request allows; the message says why and is safe to show. */
public class KsqlStatementException extends Exception {

    public KsqlStatementException(String message) {
        super(message);
    }

}
