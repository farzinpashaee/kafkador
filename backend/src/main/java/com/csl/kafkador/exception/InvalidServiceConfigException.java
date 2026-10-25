package com.csl.kafkador.exception;

/** Schema Registry, Kafka Connect or ksqlDB settings that can't be saved as given, e.g. a CA certificate that isn't PEM. */
public class InvalidServiceConfigException extends Exception {

    public InvalidServiceConfigException(String message) {
        super(message);
    }

}
