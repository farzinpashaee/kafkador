package com.csl.kafkador.util.metrics;

import java.util.Locale;
import java.util.Map;

/**
 * One metric value decoded from a Confluent metrics topic.
 *
 * @param broker the broker that reported it, when known
 * @param topic  the topic it's about, when it's a topic or partition metric
 * @param labels its other labels (partition, request type, ...), without broker and topic
 * @param kind   what the value is: gauge, counter, rate, count or mean
 */
public record MetricSample(String name, String broker, String topic, Map<String, String> labels,
                           double value, long timestamp, String kind) {

    /** Whether a label holds the reporting broker's id, as telemetry resource labels name it. */
    static boolean isBrokerLabel(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("broker") || k.endsWith("broker.id") || k.endsWith("broker_id");
    }

    static boolean isTopicLabel(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("topic") || k.endsWith(".topic") || k.endsWith("_topic");
    }

}
