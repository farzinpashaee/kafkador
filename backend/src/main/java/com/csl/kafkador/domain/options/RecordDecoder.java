package com.csl.kafkador.domain.options;

/**
 * Turns the raw bytes of a record key or value into the text shown for it, or null for a null key/value. A plain
 * {@link RecordSerde} is one; reading with Schema Registry needs one bound to the cluster's registry.
 */
@FunctionalInterface
public interface RecordDecoder {

    String decode(byte[] data);

}
