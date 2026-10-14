package com.csl.kafkador.util.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.csl.kafkador.util.metrics.ProtoWriter.message;
import static org.assertj.core.api.Assertions.assertThat;

class ConfluentMetricsDecoderTest {

    private static final long RECORD_TIME = 1_700_000_000_000L;
    private static final long MESSAGE_TIME = 1_700_000_500_000L;

    @Test
    void decodesYammerMetricsNamedByMBean() {
        ProtoWriter meter = message()
                .message(1, yammerName("kafka.server", "BrokerTopicMetrics", "MessagesInPerSec",
                        "kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec,topic=orders"))
                .varint(2, 1000)
                .doubleValue(3, 12.5);
        ProtoWriter gauge = message()
                .message(1, yammerName("kafka.log", "Log", "Size", "kafka.log:type=Log,name=Size,topic=orders,partition=0"))
                .string(2, "2048")
                .varint(3, 2048);
        ProtoWriter histogram = message()
                .message(1, yammerName("kafka.network", "RequestMetrics", "TotalTimeMs",
                        "kafka.network:type=RequestMetrics,name=TotalTimeMs,request=Produce"))
                .varint(2, 77);
        byte[] record = message()
                .varint(1, 1)
                .varint(2, MESSAGE_TIME)
                .message(4, gauge)
                .message(5, meter)
                .message(6, histogram)
                .varint(9, 3)
                .string(10, "cluster-1")
                .build();

        List<MetricSample> samples = ConfluentMetricsDecoder.decode(record, RECORD_TIME);

        assertThat(samples).containsExactly(
                new MetricSample("kafka.log:Log:Size", "3", "orders", Map.of("partition", "0"), 2048, MESSAGE_TIME, "gauge"),
                new MetricSample("kafka.server:BrokerTopicMetrics:MessagesInPerSec", "3", "orders", Map.of(), 12.5, MESSAGE_TIME, "rate"),
                new MetricSample("kafka.network:RequestMetrics:TotalTimeMs", "3", null, Map.of("request", "Produce"), 77, MESSAGE_TIME, "count"));
    }

    @Test
    void decodesKafkaMeasurableWithTags() {
        ProtoWriter metricName = message()
                .string(1, "request-rate")
                .string(2, "producer-metrics")
                .message(3, message().string(1, "client-id").string(2, "p1"));
        byte[] record = message()
                .message(3, message().message(1, metricName).doubleValue(2, 3.0))
                .varint(9, 1)
                .build();

        assertThat(ConfluentMetricsDecoder.decode(record, RECORD_TIME)).containsExactly(
                new MetricSample("producer-metrics:request-rate", "1", null, Map.of("client-id", "p1"), 3.0, RECORD_TIME, "gauge"));
    }

    @Test
    void namesYammerMetricFromItsPartsWithoutAnMBean() {
        ProtoWriter name = message().string(1, "kafka.controller").string(2, "ActiveControllerCount").string(3, "KafkaController");
        byte[] record = message().message(4, message().message(1, name).doubleValue(4, 1)).build();

        assertThat(ConfluentMetricsDecoder.decode(record, RECORD_TIME)).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("kafka.controller:KafkaController:ActiveControllerCount");
            assertThat(s.broker()).isNull();
            assertThat(s.value()).isEqualTo(1.0);
        });
    }

    @Test
    void returnsNothingForInvalidBytes() {
        assertThat(ConfluentMetricsDecoder.decode(new byte[]{(byte) 0xFF, (byte) 0xFF}, RECORD_TIME)).isEmpty();
    }

    private static ProtoWriter yammerName(String group, String type, String name, String mBean) {
        return message().string(1, group).string(2, name).string(3, type).string(5, mBean);
    }

}
