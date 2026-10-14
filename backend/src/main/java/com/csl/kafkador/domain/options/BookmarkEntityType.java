package com.csl.kafkador.domain.options;

/** Kinds of cluster entities that can be bookmarked. Stored by name, so only ever add values. */
public enum BookmarkEntityType {
    TOPIC,
    BROKER,
    CONSUMER_GROUP,
    KSQL_STREAM,
    KSQL_TABLE,
    KSQL_QUERY
}
