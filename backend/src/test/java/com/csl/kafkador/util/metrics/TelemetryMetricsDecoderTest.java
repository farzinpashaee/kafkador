package com.csl.kafkador.util.metrics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.csl.kafkador.util.metrics.ProtoWriter.message;
import static org.assertj.core.api.Assertions.assertThat;

class TelemetryMetricsDecoderTest {

    private static final long RECORD_TIME = 1_700_000_000_000L;

    @Test
    void decodesOpenCensusMetricWithResourceBrokerAndTopicLabel() {
        ProtoWriter descriptor = message()
                .string(1, "io.confluent.kafka.server/server/received_bytes")
                .varint(4, 4) // CUMULATIVE_INT64
                .message(5, message().string(1, "topic"))
                .message(5, message().string(1, "partition"));
        ProtoWriter point = message()
                .message(1, message().varint(1, 1_700_000_100L).varint(2, 500_000_000))
                .varint(2, 4096);
        ProtoWriter timeseries = message()
                .message(2, message().string(1, "orders").varint(2, 1))
                .message(2, message().string(1, "3").varint(2, 1))
                .message(3, point);
        ProtoWriter resource = message()
                .string(1, "kafka")
                .message(2, message().string(1, "kafka.broker.id").string(2, "2"))
                .message(2, message().string(1, "kafka.cluster.id").string(2, "abc"));
        byte[] record = message().message(1, descriptor).message(2, timeseries).message(3, resource).build();

        List<MetricSample> samples = TelemetryMetricsDecoder.decode(record, RECORD_TIME);

        assertThat(samples).containsExactly(new MetricSample("io.confluent.kafka.server/server/received_bytes",
                "2", "orders", Map.of("partition", "3"), 4096, 1_700_000_100_500L, "counter"));
    }

    @Test
    void decodesOpenCensusDoubleGaugeWithoutLabels() {
        byte[] record = message()
                .message(1, message().string(1, "io.confluent.kafka.server/request_queue/size").varint(4, 2))
                .message(2, message().message(3, message().doubleValue(3, 1.5)))
                .message(3, message().message(2, message().string(1, "kafka.broker.id").string(2, "1")))
                .build();

        assertThat(TelemetryMetricsDecoder.decode(record, RECORD_TIME)).singleElement().satisfies(s -> {
            assertThat(s.broker()).isEqualTo("1");
            assertThat(s.topic()).isNull();
            assertThat(s.value()).isEqualTo(1.5);
            assertThat(s.timestamp()).isEqualTo(RECORD_TIME);
            assertThat(s.kind()).isEqualTo("gauge");
        });
    }

    @Test
    void decodesOtlpResourceMetricsWithSumAndHistogram() {
        ProtoWriter sumPoint = message()
                .fixed64(3, 1_700_000_200_000_000_000L)
                .fixed64(6, 42)
                .message(7, keyValue("topic", "payments"));
        ProtoWriter histogramPoint = message()
                .fixed64(3, 1_700_000_200_000_000_000L)
                .fixed64(4, 4)
                .doubleValue(5, 10.0)
                .message(9, keyValue("request", "Produce"));
        ProtoWriter scopeMetrics = message()
                .message(2, message().string(1, "io.confluent.kafka.server/server/messages_in")
                        .message(7, message().message(1, sumPoint).varint(2, 1).varint(3, 1)))
                .message(2, message().string(1, "io.confluent.kafka.server/request/latency")
                        .message(9, message().message(1, histogramPoint)));
        ProtoWriter resource = message().message(1, keyValue("kafka.broker.id", "5"));
        byte[] record = message().message(1, resource).message(2, scopeMetrics).build();

        List<MetricSample> samples = TelemetryMetricsDecoder.decode(record, RECORD_TIME);

        assertThat(samples).containsExactly(
                new MetricSample("io.confluent.kafka.server/server/messages_in", "5", "payments", Map.of(), 42, 1_700_000_200_000L, "counter"),
                new MetricSample("io.confluent.kafka.server/request/latency", "5", null, Map.of("request", "Produce"), 2.5, 1_700_000_200_000L, "mean"));
    }

    @Test
    void decodesOtlpExportRequestWrappingResourceMetrics() {
        ProtoWriter gauge = message().string(1, "cpu").message(5, message().message(1, message().doubleValue(4, 0.25)));
        ProtoWriter resourceMetrics = message()
                .message(1, message().message(1, keyValueInt("broker.id", 7)))
                .message(2, message().message(2, gauge));
        byte[] record = message().message(1, resourceMetrics).build();

        assertThat(TelemetryMetricsDecoder.decode(record, RECORD_TIME)).singleElement()
                .isEqualTo(new MetricSample("cpu", "7", null, Map.of(), 0.25, RECORD_TIME, "gauge"));
    }

    @Test
    void decodesBareOtlpMetric() {
        byte[] record = message().string(1, "bytes").message(5, message().message(1, message().fixed64(6, 9))).build();

        assertThat(TelemetryMetricsDecoder.decode(record, RECORD_TIME)).extracting(MetricSample::value).containsExactly(9.0);
    }

    @Test
    void returnsNothingForRecordsThatArentMetrics() {
        assertThat(TelemetryMetricsDecoder.decode("{\"not\":\"protobuf\"}".getBytes(StandardCharsets.UTF_8), RECORD_TIME)).isEmpty();
        assertThat(TelemetryMetricsDecoder.decode(new byte[0], RECORD_TIME)).isEmpty();
    }

    private static ProtoWriter keyValue(String key, String value) {
        return message().string(1, key).message(2, message().string(1, value));
    }

    private static ProtoWriter keyValueInt(String key, long value) {
        return message().string(1, key).message(2, message().varint(3, value));
    }

}
