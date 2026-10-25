package com.csl.kafkador.domain.options;

/** How Kafkador signs in to Schema Registry, Kafka Connect or ksqlDB. */
public enum ServiceAuthMethod {
    /** No credentials. */
    NONE,
    /** HTTP Basic: a username and password, or an API key and secret (Confluent Cloud, Aiven). */
    BASIC,
    /** A bearer token in the Authorization header. */
    BEARER
}
