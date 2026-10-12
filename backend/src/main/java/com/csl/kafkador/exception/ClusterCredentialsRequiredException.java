package com.csl.kafkador.exception;

/**
 * Connecting needs (different) credentials: the cluster rejected the ones given, or didn't answer an
 * unauthenticated client, which is how a SASL or SSL listener treats a plaintext connection.
 */
public class ClusterCredentialsRequiredException extends Exception {

    public static final String CODE = "CREDENTIALS_REQUIRED";

    public ClusterCredentialsRequiredException(String message) {
        super(message);
    }

}
