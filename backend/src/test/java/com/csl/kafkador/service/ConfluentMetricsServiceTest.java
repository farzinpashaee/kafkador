package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.EntityMetricsDto;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Point;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Series;
import com.csl.kafkador.service.ConfluentMetricsService.Scope;
import com.csl.kafkador.service.ConfluentMetricsService.Tagged;
import com.csl.kafkador.util.metrics.MetricSample;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConfluentMetricsServiceTest {

    private static final String METRICS = ConfluentMetricsService.METRICS_TOPIC;
    private static final TopicPartition P0 = new TopicPartition(METRICS, 0);


    @Test
    void reportsMissingTopicsWithoutReadingAnything() {
        TestConsumer consumer = new TestConsumer();

        EntityMetricsDto result = read(consumer, Scope.BROKER, "1");

        assertThat(result.getSources()).extracting(EntityMetricsDto.Source::getTopic, EntityMetricsDto.Source::isExists)
                .containsExactly(tuple(ConfluentMetricsService.TELEMETRY_TOPIC, false), tuple(METRICS, false));
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void readsTheMetricsTopicAndKeepsTheBrokersOwnMetrics() {
        TestConsumer consumer = new TestConsumer();
        consumer.updatePartitions(METRICS, List.of(new PartitionInfo(METRICS, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 3L));
        consumer.schedulePollTask(() -> {
            consumer.addRecord(record(0, 1000, gauge(1, null, 5)));
            consumer.addRecord(record(1, 2000, gauge(1, "orders", 9)));
            consumer.addRecord(record(2, 3000, new byte[]{(byte) 0xFF}));
        });

        EntityMetricsDto result = read(consumer, Scope.BROKER, "1");

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
    void startsAtTheFirstRecordOfTheWindow() {
        TestConsumer consumer = new TestConsumer();
        consumer.updatePartitions(METRICS, List.of(new PartitionInfo(METRICS, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 3L));
        consumer.recentOffsets.put(P0, 2L);
        consumer.schedulePollTask(() -> consumer.addRecord(record(2, 3000, gauge(1, null, 7))));

        EntityMetricsDto result = read(consumer, Scope.BROKER, "1");

        assertThat(result.getSources().get(1).getScannedRecords()).isEqualTo(1);
        assertThat(result.getSeries()).singleElement().satisfies(s -> assertThat(s.getLatest()).isEqualTo(7.0));
    }

    @Test
    void skipsAPartitionWithNothingInTheWindow() {
        TestConsumer consumer = new TestConsumer();
        consumer.updatePartitions(METRICS, List.of(new PartitionInfo(METRICS, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 3L));
        consumer.recentOffsets.put(P0, null);

        EntityMetricsDto result = read(consumer, Scope.BROKER, "1");

        assertThat(result.getSources().get(1).getScannedRecords()).isZero();
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void oneReadAnswersEveryBrokerAndTopic() {
        TestConsumer consumer = new TestConsumer();
        consumer.updatePartitions(METRICS, List.of(new PartitionInfo(METRICS, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 3L));
        consumer.schedulePollTask(() -> {
            consumer.addRecord(record(0, 1000, gauge(1, null, 5)));
            consumer.addRecord(record(1, 2000, gauge(2, null, 6)));
            consumer.addRecord(record(2, 3000, gauge(1, "orders", 9)));
        });
        ConfluentMetricsService.Scan scan = ConfluentMetricsService.scanAll(() -> consumer, Runnable::run);

        assertThat(ConfluentMetricsService.select(scan, Scope.BROKER, "1").getSeries())
                .extracting(Series::getLatest).containsExactly(5.0);
        assertThat(ConfluentMetricsService.select(scan, Scope.BROKER, "2").getSeries())
                .extracting(Series::getLatest).containsExactly(6.0);
        EntityMetricsDto orders = ConfluentMetricsService.select(scan, Scope.TOPIC, "orders");
        assertThat(orders.getSeries()).extracting(Series::getLatest).containsExactly(9.0);
        assertThat(orders.getSources().get(1).getMatchedSamples()).isEqualTo(1);
        assertThat(orders.getReadAt()).isEqualTo(scan.readAt());
    }

    @Test
    void cacheKeyTellsCredentialsApart() {
        Properties alice = new Properties();
        alice.put("bootstrap.servers", "localhost:9092");
        alice.put("sasl.jaas.config", "user=alice");
        Properties bob = new Properties();
        bob.putAll(alice);
        bob.put("sasl.jaas.config", "user=bob");
        Properties aliceAgain = new Properties();
        aliceAgain.putAll(alice);

        assertThat(ConfluentMetricsService.cacheKey(alice)).isEqualTo(ConfluentMetricsService.cacheKey(aliceAgain))
                .isNotEqualTo(ConfluentMetricsService.cacheKey(bob)).doesNotContain("alice");
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

    private static EntityMetricsDto read(TestConsumer consumer, Scope scope, String target) {
        return ConfluentMetricsService.select(ConfluentMetricsService.scanAll(() -> consumer, Runnable::run), scope, target);
    }

    /** A MockConsumer that answers offsetsForTimes and stays open, so one instance can serve every read. */
    private static class TestConsumer extends MockConsumer<byte[], byte[]> {
        /** Each partition's first offset in the window, or null for none; the beginning when not given. */
        final Map<TopicPartition, Long> recentOffsets = new HashMap<>();

        TestConsumer() {
            super("earliest");
        }

        @Override
        public synchronized Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestamps, Duration timeout) {
            Map<TopicPartition, OffsetAndTimestamp> result = new HashMap<>();
            timestamps.forEach((tp, t) -> {
                Long offset = recentOffsets.containsKey(tp) ? recentOffsets.get(tp) : Long.valueOf(0L);
                result.put(tp, offset == null ? null : new OffsetAndTimestamp(offset, t));
            });
            return result;
        }

        @Override
        public synchronized void close() {
        }

        @Override
        public synchronized void close(Duration timeout) {
        }
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
