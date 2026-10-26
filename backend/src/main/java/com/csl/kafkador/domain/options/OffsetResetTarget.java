package com.csl.kafkador.domain.options;

/** Where a consumer group's offsets are moved to, as with kafka-consumer-groups --reset-offsets. */
public enum OffsetResetTarget {
    /** The first offset still in the partition (--to-earliest). */
    EARLIEST,
    /** The end of the partition, so only new records are read (--to-latest). */
    LATEST,
    /** The first record written at or after a point in time; the end when there's none (--to-datetime). */
    TIMESTAMP,
    /** A given offset, kept within what the partition holds (--to-offset). */
    OFFSET
}
