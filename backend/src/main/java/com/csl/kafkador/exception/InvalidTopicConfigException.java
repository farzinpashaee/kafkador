package com.csl.kafkador.exception;

/** The cluster rejected a topic because of its settings (bad config, replication factor or partition count). */
public class InvalidTopicConfigException extends Exception {

    public InvalidTopicConfigException(String message) {
        super(message);
    }

}
