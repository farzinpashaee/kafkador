package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.EntityMetricsDto;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Point;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Series;
import com.csl.kafkador.service.ConfluentMetricsService.Scope;
import com.csl.kafkador.service.ConfluentMetricsService.Tagged;
import com.csl.kafkador.util.metrics.MetricSample;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class ConfluentMetricsServiceTest {

    private static final String METRICS = ConfluentMetricsService.METRICS_TOPIC;
    private static final TopicPartition P0 = new TopicPartition(METRICS, 0);

    private final ConfluentMetricsService service = new ConfluentMetricsService(mock(ConnectionService.class));

    @Test
    void reportsMissingTopicsWithoutReadingAnything() {
        MockConsumer<byte[], byte[]> consumer = new MockConsumer<>("earliest");

        EntityMetricsDto result = service.read(consumer, Scope.BROKER, "1");

        assertThat(result.getSources()).extracting(EntityMetricsDto.Source::getTopic, EntityMetricsDto.Source::isExists)
                .containsExactly(tuple(ConfluentMetricsService.TELEMETRY_TOPIC, false), tuple(METRICS, false));
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void readsTheMetricsTopicAndKeepsTheBrokersOwnMetrics() {
        MockConsumer<byte[], byte[]> consumer = new MockConsumer<>("earliest");
        consumer.updatePartitions(METRICS, List.of(new PartitionInfo(METRICS, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 3L));
        consumer.schedulePollTask(() -> {
            consumer.addRecord(record(0, 1000, gauge(1, null, 5)));
            consumer.addRecord(record(1, 2000, gauge(1, "orders", 9)));
            consumer.addRecord(record(2, 3000, new byte[]{(byte) 0xFF}));
        });

        EntityMetricsDto result = service.read(consumer, Scope.BROKER, "1");

        EntityMetricsDto.Source source = result.getSources().get(1);
        assertThat(source.isExists()).isTrue();
        assertThat(source.getScannedRecords()).isEqualTo(3);
        assertThat(source.getUndecodedRecords()).isEqualTo(1);
        assertThat(source.getMatchedSamples()).isEqualTo(1);
        assertThat(source.getFromTimestamp()).isEqualTo(1000L);
        assertThat(source.getToTimestamp()).isEqualTo(3000L);
        assertThat(result.getSeries()).singleElement().satisfies(s -> {
            assertThat(s.getName()).isEqualTo("kafka.server:ReplicaManager:LeaderCount");
            assertThat(s.getLatest()).isEqualTo(5.0);
            assertThat(s.getSource()).isEqualTo(METRICS);
        });
    }

    @Test
    void topicScopeKeepsOnlyThatTopicsSamplesFromAnyBroker() {
        MetricSample own = sample("1", "orders", 1, 10);
        MetricSample otherBroker = sample("2", "orders", 1, 20);
        MetricSample otherTopic = sample("1", "payments", 1, 30);
        MetricSample brokerWide = sample("1", null, 1, 40);

        assertThat(ConfluentMetricsService.matches(Scope.TOPIC, "orders", own)).isTrue();
        assertThat(ConfluentMetricsService.matches(Scope.TOPIC, "orders", otherBroker)).isTrue();
        assertThat(ConfluentMetricsService.matches(Scope.TOPIC, "orders", otherTopic)).isFalse();
        assertThat(ConfluentMetricsService.matches(Scope.BROKER, "1", brokerWide)).isTrue();
        assertThat(ConfluentMetricsService.matches(Scope.BROKER, "1", own)).isFalse();
        assertThat(ConfluentMetricsService.matches(Scope.BROKER, "2", brokerWide)).isFalse();
    }

    @Test
    void aggregateGroupsByBrokerAndLabelsAndOrdersPoints() {
        List<Tagged> matches = List.of(
                new Tagged(METRICS, sample("1", "orders", 3000, 3)),
                new Tagged(METRICS, sample("1", "orders", 1000, 1)),
                new Tagged(METRICS, sample("2", "orders", 2000, 7)),
                new Tagged(METRICS, sample("1", "orders", 2000, 2)));

        List<Series> series = ConfluentMetricsService.aggregate(matches);

        assertThat(series).extracting(Series::getBroker, Series::getLatest, Series::getLatestTimestamp)
                .containsExactly(tuple("1", 3.0, 3000L), tuple("2", 7.0, 2000L));
        assertThat(series.get(0).getPoints()).containsExactly(new Point(1000, 1), new Point(2000, 2), new Point(3000, 3));
    }

    @Test
    void aggregateKeepsOnlyTheNewestPoints() {
        List<Tagged> matches = new java.util.ArrayList<>();
        for (int i = 0; i < ConfluentMetricsService.MAX_POINTS + 10; i++) matches.add(new Tagged(METRICS, sample("1", null, i, i)));

        Series series = ConfluentMetricsService.aggregate(matches).get(0);

        assertThat(series.getPoints()).hasSize(ConfluentMetricsService.MAX_POINTS);
        assertThat(series.getPoints().get(0).t()).isEqualTo(10L);
    }

    private static MetricSample sample(String broker, String topic, long timestamp, double value) {
        return new MetricSample("bytes-in", broker, topic, Map.of(), value, timestamp, "rate");
    }

    /** A MetricsMessage holding one gauge from {@code broker}, about {@code topic} when given. */
    private static byte[] gauge(int broker, String topic, double value) {
        String mBean = "kafka.server:type=ReplicaManager,name=LeaderCount" + (topic == null ? "" : ",topic=" + topic);
        byte[] name = lengthDelimited(5, mBean.getBytes());
        byte[] gauge = concat(lengthDelimited(1, name), doubleField(4, value));
        return concat(lengthDelimited(4, gauge), new byte[]{(byte) (9 << 3), (byte) broker});
    }

    private static byte[] lengthDelimited(int field, byte[] value) {
        return concat(new byte[]{(byte) (field << 3 | 2), (byte) value.length}, value);
    }

    private static byte[] doubleField(int field, double value) {
        return concat(new byte[]{(byte) (field << 3 | 1)},
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static ConsumerRecord<byte[], byte[]> record(long offset, long timestamp, byte[] value) {
        return new ConsumerRecord<>(METRICS, 0, offset, timestamp, TimestampType.CREATE_TIME, 0, value.length,
                null, value, new RecordHeaders(), Optional.empty());
    }

}
