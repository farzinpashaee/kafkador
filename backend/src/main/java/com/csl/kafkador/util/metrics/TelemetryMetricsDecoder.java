package com.csl.kafkador.util.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Decodes records of {@code _confluent-telemetry-metrics}, which the Confluent Telemetry Reporter writes as protobuf.
 * Depending on the Confluent Platform version a record holds an OpenCensus {@code Metric}, or OpenTelemetry
 * {@code Metric}, {@code ResourceMetrics} or {@code ExportMetricsServiceRequest}; the layout is told apart from the
 * shape of the first field. Histograms and summaries are reported as their mean.
 */
public final class TelemetryMetricsDecoder {

    private TelemetryMetricsDecoder() {}

    /** The samples in one record; empty when the record isn't in a recognised layout. */
    public static List<MetricSample> decode(byte[] value, long recordTimestamp) {
        Optional<ProtoMessage> parsed = ProtoMessage.tryParse(value);
        if (parsed.isEmpty() || parsed.get().fields().isEmpty()) return List.of();
        ProtoMessage message = parsed.get();
        List<MetricSample> samples = new ArrayList<>();
        if (isOtlpMetric(message)) {
            otlpMetric(message, Map.of(), recordTimestamp, samples);
        } else if (isOpenCensusMetric(message)) {
            openCensusMetric(message, recordTimestamp, samples);
        } else if (message.has(2)) {
            otlpResourceMetrics(message, recordTimestamp, samples);
        } else {
            message.messages(1).forEach(resourceMetrics -> otlpResourceMetrics(resourceMetrics, recordTimestamp, samples));
        }
        return samples;
    }

    // OpenTelemetry: Metric{name=1, gauge=5, sum=7, histogram=9, exponential_histogram=10, summary=11}

    private static boolean isOtlpMetric(ProtoMessage message) {
        return message.string(1).filter(TelemetryMetricsDecoder::isMetricName).isPresent()
                && (message.has(5) || message.has(7) || message.has(9) || message.has(10) || message.has(11));
    }

    private static void otlpResourceMetrics(ProtoMessage resourceMetrics, long recordTimestamp, List<MetricSample> samples) {
        Map<String, String> resource = resourceMetrics.message(1).map(r -> otlpAttributes(r, 1)).orElse(Map.of());
        for (ProtoMessage scopeMetrics : resourceMetrics.messages(2)) {
            for (ProtoMessage metric : scopeMetrics.messages(2)) {
                if (isOtlpMetric(metric)) otlpMetric(metric, resource, recordTimestamp, samples);
            }
        }
    }

    private static void otlpMetric(ProtoMessage metric, Map<String, String> resource, long recordTimestamp,
                                   List<MetricSample> samples) {
        String name = metric.string(1).orElseThrow();
        metric.message(5).ifPresent(gauge -> gauge.messages(1).forEach(point ->
                otlpNumberPoint(name, "gauge", point, resource, recordTimestamp, samples)));
        metric.message(7).ifPresent(sum -> {
            String kind = sum.varint(3).orElse(0) == 1 ? "counter" : "gauge";
            sum.messages(1).forEach(point -> otlpNumberPoint(name, kind, point, resource, recordTimestamp, samples));
        });
        // Histogram and summary points keep attributes at 9 and 7, exponential histogram points at 1; all have count=4, sum=5.
        metric.message(9).ifPresent(h -> h.messages(1).forEach(point ->
                otlpMeanPoint(name, point, 9, resource, recordTimestamp, samples)));
        metric.message(10).ifPresent(h -> h.messages(1).forEach(point ->
                otlpMeanPoint(name, point, 1, resource, recordTimestamp, samples)));
        metric.message(11).ifPresent(s -> s.messages(1).forEach(point ->
                otlpMeanPoint(name, point, 7, resource, recordTimestamp, samples)));
    }

    /** NumberDataPoint{time_unix_nano=3, as_double=4, as_int=6, attributes=7}. */
    private static void otlpNumberPoint(String name, String kind, ProtoMessage point, Map<String, String> resource,
                                        long recordTimestamp, List<MetricSample> samples) {
        OptionalDouble asDouble = point.doubleValue(4);
        OptionalLong asInt = point.fixed64(6);
        if (asDouble.isEmpty() && asInt.isEmpty()) return;
        double value = asDouble.isPresent() ? asDouble.getAsDouble() : asInt.getAsLong();
        samples.add(sample(name, kind, value, nanosToMillis(point.fixed64(3), recordTimestamp),
                resource, otlpAttributes(point, 7)));
    }

    private static void otlpMeanPoint(String name, ProtoMessage point, int attributesField, Map<String, String> resource,
                                      long recordTimestamp, List<MetricSample> samples) {
        long count = point.fixed64(4).orElse(0);
        OptionalDouble sum = point.doubleValue(5);
        if (count <= 0 || sum.isEmpty()) return;
        samples.add(sample(name, "mean", sum.getAsDouble() / count, nanosToMillis(point.fixed64(3), recordTimestamp),
                resource, otlpAttributes(point, attributesField)));
    }

    /** KeyValue{key=1, value=AnyValue{string=1, bool=2, int=3, double=4}}. */
    private static Map<String, String> otlpAttributes(ProtoMessage holder, int field) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (ProtoMessage keyValue : holder.messages(field)) {
            Optional<String> key = keyValue.string(1);
            Optional<ProtoMessage> any = keyValue.message(2);
            if (key.isEmpty() || any.isEmpty()) continue;
            ProtoMessage v = any.get();
            String text = v.string(1)
                    .or(() -> v.varint(2).isPresent() ? Optional.of(String.valueOf(v.varint(2).getAsLong() != 0)) : Optional.empty())
                    .or(() -> v.varint(3).isPresent() ? Optional.of(String.valueOf(v.varint(3).getAsLong())) : Optional.empty())
                    .or(() -> v.doubleValue(4).isPresent() ? Optional.of(String.valueOf(v.doubleValue(4).getAsDouble())) : Optional.empty())
                    .orElse("");
            attributes.put(key.get(), text);
        }
        return attributes;
    }

    // OpenCensus: Metric{metric_descriptor=1{name=1, type=4, label_keys=5{key=1}}, timeseries=2, resource=3{labels=2}}

    private static boolean isOpenCensusMetric(ProtoMessage message) {
        return message.message(1).flatMap(d -> d.string(1)).filter(TelemetryMetricsDecoder::isMetricName).isPresent()
                && message.has(2);
    }

    private static void openCensusMetric(ProtoMessage metric, long recordTimestamp, List<MetricSample> samples) {
        ProtoMessage descriptor = metric.message(1).orElseThrow();
        String name = descriptor.string(1).orElseThrow();
        long type = descriptor.varint(4).orElse(0);
        // Types 4-6 are cumulative; 7 is a summary.
        String kind = type >= 4 && type <= 6 ? "counter" : "gauge";
        List<String> labelKeys = descriptor.messages(5).stream().map(k -> k.string(1).orElse("")).toList();
        Map<String, String> resource = new LinkedHashMap<>();
        metric.message(3).ifPresent(r -> r.messages(2).forEach(entry ->
                entry.string(1).ifPresent(k -> resource.put(k, entry.string(2).orElse("")))));

        for (ProtoMessage timeseries : metric.messages(2)) {
            Map<String, String> labels = new LinkedHashMap<>();
            List<ProtoMessage> values = timeseries.messages(2);
            for (int i = 0; i < values.size() && i < labelKeys.size(); i++) {
                if (values.get(i).varint(2).orElse(1) != 0) labels.put(labelKeys.get(i), values.get(i).string(1).orElse(""));
            }
            for (ProtoMessage point : timeseries.messages(3)) {
                long timestamp = point.message(1)
                        .map(t -> t.varint(1).orElse(0) * 1000 + t.varint(2).orElse(0) / 1_000_000)
                        .filter(t -> t > 0).orElse(recordTimestamp);
                Double value = openCensusValue(point);
                if (value == null) continue;
                String pointKind = point.has(4) || point.has(5) ? "mean" : kind;
                samples.add(sample(name, pointKind, value, timestamp, resource, labels));
            }
        }
    }

    /** Point{int64_value=2, double_value=3, distribution_value=4{count=1, sum=2}, summary_value=5{count=1{value=1}, sum=2{value=1}}}. */
    private static Double openCensusValue(ProtoMessage point) {
        if (point.varint(2).isPresent()) return (double) point.varint(2).getAsLong();
        if (point.doubleValue(3).isPresent()) return point.doubleValue(3).getAsDouble();
        Optional<ProtoMessage> distribution = point.message(4);
        if (distribution.isPresent()) {
            long count = distribution.get().varint(1).orElse(0);
            OptionalDouble sum = distribution.get().doubleValue(2);
            return count > 0 && sum.isPresent() ? sum.getAsDouble() / count : null;
        }
        Optional<ProtoMessage> summary = point.message(5);
        if (summary.isPresent()) {
            long count = summary.get().message(1).map(c -> c.varint(1).orElse(0)).orElse(0L);
            OptionalDouble sum = summary.get().message(2).map(s -> s.doubleValue(1)).orElse(OptionalDouble.empty());
            return count > 0 && sum.isPresent() ? sum.getAsDouble() / count : null;
        }
        return null;
    }

    private static MetricSample sample(String name, String kind, double value, long timestamp,
                                       Map<String, String> resource, Map<String, String> pointLabels) {
        String broker = null;
        String topic = null;
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, String> label : resource.entrySet()) {
            if (MetricSample.isBrokerLabel(label.getKey())) broker = label.getValue();
        }
        for (Map.Entry<String, String> label : pointLabels.entrySet()) {
            if (MetricSample.isBrokerLabel(label.getKey())) broker = label.getValue();
            else if (MetricSample.isTopicLabel(label.getKey())) topic = label.getValue();
            else labels.put(label.getKey(), label.getValue());
        }
        return new MetricSample(name, broker, topic, labels, value, timestamp, kind);
    }

    private static long nanosToMillis(OptionalLong nanos, long fallback) {
        return nanos.isPresent() && nanos.getAsLong() > 0 ? nanos.getAsLong() / 1_000_000 : fallback;
    }

    private static boolean isMetricName(String name) {
        return !name.isBlank() && name.chars().noneMatch(Character::isISOControl);
    }

}
