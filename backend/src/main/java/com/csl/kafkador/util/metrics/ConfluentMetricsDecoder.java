package com.csl.kafkador.util.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Decodes records of {@code _confluent-metrics}, which the Confluent Metrics Reporter writes as a protobuf
 * {@code MetricsMessage}: {metricType=1, timestamp=2, kafkaMeasurable=3, yammerGauge=4, yammerMeter=5,
 * yammerHistogram=6, yammerTimer=7, clientId=8, brokerId=9, clusterId=10}.
 * <p>
 * Yammer metrics are named by their JMX MBean ({@code kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec,topic=t}),
 * which also carries the topic and partition. Meters report their one-minute rate, histograms and timers their count.
 */
public final class ConfluentMetricsDecoder {

    private static final int KAFKA_MEASURABLE = 3;
    private static final int YAMMER_GAUGE = 4;
    private static final int YAMMER_METER = 5;
    private static final int YAMMER_HISTOGRAM = 6;
    private static final int YAMMER_TIMER = 7;
    private static final int BROKER_ID = 9;
    /** Epoch milliseconds from 2001 on; anything smaller in the timestamp field isn't one. */
    private static final long MIN_EPOCH_MILLIS = 1_000_000_000_000L;

    private ConfluentMetricsDecoder() {}

    public static List<MetricSample> decode(byte[] value, long recordTimestamp) {
        Optional<ProtoMessage> parsed = ProtoMessage.tryParse(value);
        if (parsed.isEmpty()) return List.of();
        ProtoMessage message = parsed.get();
        long timestamp = message.varint(2).stream().filter(t -> t >= MIN_EPOCH_MILLIS).findFirst().orElse(recordTimestamp);
        String broker = message.varint(BROKER_ID).isPresent() ? String.valueOf(message.varint(BROKER_ID).getAsLong()) : null;

        List<MetricSample> samples = new ArrayList<>();
        for (ProtoMessage measurable : message.messages(KAFKA_MEASURABLE)) {
            OptionalDouble v = measurable.doubleValue(2);
            Name name = kafkaMetricName(measurable);
            if (name != null && v.isPresent()) samples.add(name.sample(broker, v.getAsDouble(), timestamp, "gauge"));
        }
        for (ProtoMessage gauge : message.messages(YAMMER_GAUGE)) {
            Name name = yammerMetricName(gauge);
            Double v = gaugeValue(gauge);
            if (name != null && v != null) samples.add(name.sample(broker, v, timestamp, "gauge"));
        }
        for (ProtoMessage meter : message.messages(YAMMER_METER)) {
            Name name = yammerMetricName(meter);
            OptionalDouble rate = meter.doubleValue(3);
            if (name != null && rate.isPresent()) samples.add(name.sample(broker, rate.getAsDouble(), timestamp, "rate"));
        }
        for (int field : new int[]{YAMMER_HISTOGRAM, YAMMER_TIMER}) {
            for (ProtoMessage histogram : message.messages(field)) {
                Name name = yammerMetricName(histogram);
                OptionalLong count = histogram.varint(2);
                if (name != null && count.isPresent()) samples.add(name.sample(broker, count.getAsLong(), timestamp, "count"));
            }
        }
        return samples;
    }

    /** YammerGauge{metricName=1, value=2 (text), longValue=3, doubleValue=4}. */
    private static Double gaugeValue(ProtoMessage gauge) {
        if (gauge.doubleValue(4).isPresent()) return gauge.doubleValue(4).getAsDouble();
        if (gauge.varint(3).isPresent()) return (double) gauge.varint(3).getAsLong();
        return gauge.string(2).map(text -> {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    /** YammerMetricName{group=1, name=2, type=3, scope=4, mBeanName=5}; the MBean name is preferred when present. */
    private static Name yammerMetricName(ProtoMessage metric) {
        Optional<ProtoMessage> metricName = metric.message(1);
        if (metricName.isEmpty()) return null;
        for (ProtoMessage.Field field : metricName.get().fields()) {
            if (field.bytes() == null) continue;
            Optional<String> mBean = ProtoMessage.text(field.bytes()).filter(t -> t.contains(":") && t.contains("="));
            if (mBean.isPresent()) return fromMBean(mBean.get());
        }
        Optional<String> group = metricName.get().string(1);
        Optional<String> name = metricName.get().string(2);
        if (name.isEmpty()) return null;
        String type = metricName.get().string(3).orElse("");
        String full = String.join(":", List.of(group.orElse(""), type, name.get()).stream().filter(s -> !s.isEmpty()).toList());
        return new Name(full, null, Map.of());
    }

    /** KafkaMeasurable{metricName=KafkaMetricName{name=1, group=2, tags=3 (map)}, value=2}. */
    private static Name kafkaMetricName(ProtoMessage measurable) {
        Optional<ProtoMessage> metricName = measurable.message(1);
        Optional<String> name = metricName.flatMap(m -> m.string(1));
        if (name.isEmpty()) return null;
        String group = metricName.get().string(2).orElse("");
        Map<String, String> tags = new LinkedHashMap<>();
        metricName.get().messages(3).forEach(entry -> entry.string(1).ifPresent(k -> tags.put(k, entry.string(2).orElse(""))));
        return Name.of(group.isEmpty() ? name.get() : group + ":" + name.get(), tags);
    }

    /** {@code domain:type=T,name=N,k=v} becomes the name {@code domain:T:N} with the other keys as labels. */
    static Name fromMBean(String mBean) {
        int colon = mBean.indexOf(':');
        String domain = mBean.substring(0, colon);
        Map<String, String> properties = new LinkedHashMap<>();
        for (String part : mBean.substring(colon + 1).split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) properties.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        String type = properties.remove("type");
        String name = properties.remove("name");
        StringBuilder full = new StringBuilder(domain);
        if (type != null) full.append(':').append(type);
        if (name != null) full.append(':').append(name);
        return Name.of(full.toString(), properties);
    }

    record Name(String name, String topic, Map<String, String> labels) {

        static Name of(String name, Map<String, String> tags) {
            String topic = null;
            Map<String, String> labels = new LinkedHashMap<>();
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                if (MetricSample.isTopicLabel(tag.getKey())) topic = tag.getValue();
                else if (!MetricSample.isBrokerLabel(tag.getKey())) labels.put(tag.getKey(), tag.getValue());
            }
            return new Name(name, topic, labels);
        }

        MetricSample sample(String broker, double value, long timestamp, String kind) {
            return new MetricSample(name, broker, topic, labels, value, timestamp, kind);
        }
    }

}
