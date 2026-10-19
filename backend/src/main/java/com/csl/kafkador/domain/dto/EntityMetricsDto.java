package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Recent metrics of one broker or topic, read from the Confluent metrics topics. */
@Data
@Accessors(chain = true)
public class EntityMetricsDto {

    /** Each metrics topic Kafkador looks in, whether or not it exists. */
    private List<Source> sources = new ArrayList<>();
    private List<Series> series = new ArrayList<>();
    /** More series matched than are returned. */
    private boolean seriesLimitReached;
    /** When the metrics topics were read, epoch milliseconds; a read is reused for a short while. */
    private long readAt;

    @Data
    @Accessors(chain = true)
    public static class Source {
        private String topic;
        private boolean exists;
        /** Records read from the end of the topic. */
        private int scannedRecords;
        /** Records that couldn't be decoded as metrics. */
        private int undecodedRecords;
        /** Samples about the requested broker or topic. */
        private int matchedSamples;
        /** Oldest and newest record read, epoch milliseconds. */
        private Long fromTimestamp;
        private Long toTimestamp;
        /** Why the topic couldn't be read, when it exists but reading failed. */
        private String error;
    }

    /** One metric with one set of labels, its recent values oldest first. */
    @Data
    @Accessors(chain = true)
    public static class Series {
        private String name;
        private String source;
        /** gauge, counter, rate, count or mean. */
        private String kind;
        private String broker;
        private Map<String, String> labels;
        private double latest;
        private long latestTimestamp;
        private List<Point> points = new ArrayList<>();
    }

    public record Point(long t, double v) {}

}
