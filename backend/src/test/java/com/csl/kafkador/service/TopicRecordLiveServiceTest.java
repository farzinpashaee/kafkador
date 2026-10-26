package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.RecordFilterDto;
import com.csl.kafkador.domain.dto.TopicRecordDto;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.service.registry.SchemaRegistryRecordDecoders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TopicRecordLiveServiceTest {

    private static final String TOPIC = "orders";
    private static final TopicPartition P0 = new TopicPartition(TOPIC, 0);

    private final TopicRecordLiveService service = new TopicRecordLiveService(mock(ConnectionService.class), mock(SchemaRegistryRecordDecoders.class), mock(SessionHolder.class));
    private final RecordingSink sink = new RecordingSink();
    private MockConsumer<byte[], byte[]> consumer;

    @BeforeEach
    void setUp() {
        consumer = new MockConsumer<>("earliest");
        consumer.updatePartitions(TOPIC, List.of(new PartitionInfo(TOPIC, 0, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L));
        consumer.updateEndOffsets(Map.of(P0, 5L));
    }

    @Test
    void streamsOnlyRecordsProducedAfterItStartedThatMatchTheFilters() {
        // First poll: a record from before the stream started (offset 4) and two new ones; second poll: nothing.
        consumer.schedulePollTask(() -> {
            consumer.addRecord(record(4, "old"));
            consumer.addRecord(record(5, "order-1"));
            consumer.addRecord(record(6, "payment-1"));
        });
        List<RecordFilterDto> filters = List.of(new RecordFilterDto().setField(RecordFilterDto.Field.VALUE)
                .setOperator(RecordFilterDto.Operator.CONTAINS).setValue("ORDER"));

        service.stream(consumer, TOPIC, List.of(), RecordSerde.STRING, RecordSerde.STRING, filters, sink, stopAfterPolls(2), Long.MAX_VALUE);

        assertThat(sink.ready).isTrue();
        assertThat(sink.batches).hasSize(1);
        assertThat(sink.batches.get(0)).extracting(TopicRecordDto::getValue).containsExactly("order-1");
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void reportsAPartitionTheTopicDoesNotHave() {
        service.stream(consumer, TOPIC, List.of(3), RecordSerde.STRING, RecordSerde.STRING, List.of(), sink, () -> true, Long.MAX_VALUE);

        assertThat(sink.ready).isFalse();
        assertThat(sink.failures).containsExactly("Topic 'orders' has no partition 3");
    }

    @Test
    void stopsAtItsTimeLimitAndSaysSo() {
        service.stream(consumer, TOPIC, List.of(), RecordSerde.STRING, RecordSerde.STRING, List.of(), sink, () -> true,
                System.currentTimeMillis() - 1);

        assertThat(sink.ready).isTrue();
        assertThat(sink.failures).singleElement().asString()
                .contains("stopped after 30 minutes").contains("Start it again");
    }

    @Test
    void capsLiveViewsPerSessionAndInAllAndFreesASlotWhenOneEnds() {
        service.maxPerSession = 2;
        service.maxTotal = 3;

        assertThat(service.slots.tryAcquire("alice")).isNull();
        assertThat(service.slots.tryAcquire("alice")).isNull();
        assertThat(service.slots.tryAcquire("alice")).contains("already have 2 live views open");
        assertThat(service.slots.tryAcquire("bob")).isNull();
        assertThat(service.slots.tryAcquire("carol")).contains("already running 3 live views");

        service.slots.release("alice");
        assertThat(service.slots.tryAcquire("carol")).isNull();
        assertThat(service.slots.total()).isEqualTo(3);

        service.slots.release("alice");
        service.slots.release("bob");
        service.slots.release("carol");
        assertThat(service.slots.total()).isZero();
        assertThat(service.slots.tryAcquire("alice")).isNull();
    }

    @Test
    void reportsAMissingTopic() {
        service.stream(consumer, "missing", List.of(), RecordSerde.STRING, RecordSerde.STRING, List.of(), sink, () -> true, Long.MAX_VALUE);

        assertThat(sink.failures).containsExactly("Topic 'missing' does not exist");
    }

    @Test
    void parsesAndChecksTheFiltersParameter() throws Exception {
        assertThat(TopicRecordLiveService.parseFilters(null)).isEmpty();
        assertThat(TopicRecordLiveService.parseFilters("[{\"field\":\"KEY\",\"operator\":\"EQUALS\",\"value\":\"42\"}]"))
                .singleElement().extracting(RecordFilterDto::getValue).isEqualTo("42");
        assertThatThrownBy(() -> TopicRecordLiveService.parseFilters("[{\"field\":\"KEY\",\"operator\":\"EQUALS\",\"value\":\" \"}]"))
                .isInstanceOf(InvalidRecordRequestException.class);
        assertThatThrownBy(() -> TopicRecordLiveService.parseFilters("not json"))
                .isInstanceOf(InvalidRecordRequestException.class);
    }

    /** Keeps the loop running for {@code polls} iterations. */
    private static java.util.function.BooleanSupplier stopAfterPolls(int polls) {
        AtomicInteger remaining = new AtomicInteger(polls);
        return () -> remaining.getAndDecrement() > 0;
    }

    private static ConsumerRecord<byte[], byte[]> record(long offset, String value) {
        byte[] v = value.getBytes(StandardCharsets.UTF_8);
        return new ConsumerRecord<>(TOPIC, 0, offset, 1000L + offset, TimestampType.CREATE_TIME, -1, v.length,
                null, v, new RecordHeaders(), Optional.empty());
    }

    private static final class RecordingSink implements TopicRecordLiveService.LiveSink {
        boolean ready;
        final List<List<TopicRecordDto>> batches = new ArrayList<>();
        final List<String> failures = new ArrayList<>();

        @Override
        public void ready() {
            ready = true;
        }

        @Override
        public void records(List<TopicRecordDto> records) {
            batches.add(records);
        }

        @Override
        public void heartbeat() {
        }

        @Override
        public void failure(String message) {
            failures.add(message);
        }
    }

}
