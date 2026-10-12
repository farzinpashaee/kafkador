package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.RecordFilterDto;
import com.csl.kafkador.domain.dto.TopicRecordDto;
import com.csl.kafkador.domain.dto.TopicRecordSearchRequestDto;
import com.csl.kafkador.domain.dto.TopicRecordSearchRequestDto.SeekType;
import com.csl.kafkador.domain.dto.TopicRecordSearchResultDto;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.exception.TopicNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class TopicRecordServiceTest {

    private static final String TOPIC = "orders";
    private static final TopicPartition P0 = new TopicPartition(TOPIC, 0);
    private static final TopicPartition P1 = new TopicPartition(TOPIC, 1);

    private final TopicRecordService service = new TopicRecordService(mock(ConnectionService.class));
    private MockConsumer<byte[], byte[]> consumer;

    /** Partition 0 holds offsets 0-9 (t=1000..1090), partition 1 offsets 0-4 (t=1055..1095), so newest interleaves them. */
    @BeforeEach
    void setUp() {
        consumer = new MockConsumer<>("earliest");
        consumer.updatePartitions(TOPIC, List.of(
                new PartitionInfo(TOPIC, 0, null, null, null),
                new PartitionInfo(TOPIC, 1, null, null, null)));
        consumer.updateBeginningOffsets(Map.of(P0, 0L, P1, 0L));
        consumer.updateEndOffsets(Map.of(P0, 10L, P1, 5L));
        List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) records.add(record(0, i, 1000 + i * 10L, "key-" + i, "{\"n\":" + i + "}"));
        for (int i = 0; i < 5; i++) records.add(record(1, i, 1055 + i * 10L, "other-" + i, "{\"n\":" + (100 + i) + "}"));
        // Records can only be added once the scan has assigned the partitions.
        consumer.schedulePollTask(() -> records.forEach(r -> {
            if (consumer.assignment().contains(new TopicPartition(TOPIC, r.partition()))) consumer.addRecord(r);
        }));
    }

    @Test
    void newestReturnsTheLatestRecordsAcrossPartitionsNewestFirst() throws Exception {
        TopicRecordSearchResultDto result = service.search(consumer, TOPIC, request(SeekType.NEWEST).setLimit(3));

        assertThat(result.getRecords()).extracting(TopicRecordDto::getPartition, TopicRecordDto::getOffset)
                .containsExactly(tuple(1, 4L), tuple(0, 9L), tuple(1, 3L));
        assertThat(result.isScanLimitReached()).isFalse();
    }

    @Test
    void oldestReadsForwardFromTheStartOfTheSelectedPartition() throws Exception {
        TopicRecordSearchResultDto result = service.search(consumer, TOPIC,
                request(SeekType.OLDEST).setPartitions(List.of(1)).setLimit(2));

        assertThat(result.getRecords()).extracting(TopicRecordDto::getKey).containsExactly("other-0", "other-1");
        assertThat(result.getRecords()).allMatch(r -> r.getPartition() == 1);
    }

    @Test
    void offsetSeekStartsEveryPartitionAtThatOffset() throws Exception {
        TopicRecordSearchResultDto result = service.search(consumer, TOPIC,
                request(SeekType.OFFSET).setOffset(4L).setPartitions(List.of(0, 1)).setLimit(50));

        assertThat(result.getRecords()).extracting(TopicRecordDto::getOffset).allMatch(offset -> offset >= 4);
        assertThat(result.getRecords()).hasSize(6 + 1);
    }

    @Test
    void filtersAreAppliedToTheDecodedKeyAndValue() throws Exception {
        TopicRecordSearchRequestDto request = request(SeekType.OLDEST).setLimit(50);
        request.getFilters().add(new RecordFilterDto().setField(RecordFilterDto.Field.KEY)
                .setOperator(RecordFilterDto.Operator.CONTAINS).setValue("KEY-"));
        request.getFilters().add(new RecordFilterDto().setField(RecordFilterDto.Field.VALUE)
                .setOperator(RecordFilterDto.Operator.NOT_CONTAINS).setValue("\"n\":3"));

        TopicRecordSearchResultDto result = service.search(consumer, TOPIC, request);

        assertThat(result.getRecords()).extracting(TopicRecordDto::getKey)
                .containsExactly("key-0", "key-1", "key-2", "key-4", "key-5", "key-6", "key-7", "key-8", "key-9");
        assertThat(result.getScannedCount()).isEqualTo(15);
    }

    @Test
    void rejectsAPartitionTheTopicDoesNotHave() {
        assertThatThrownBy(() -> service.search(consumer, TOPIC, request(SeekType.NEWEST).setPartitions(List.of(7))))
                .isInstanceOf(InvalidRecordRequestException.class)
                .hasMessageContaining("no partition 7");
    }

    @Test
    void rejectsAnOffsetSeekWithoutAnOffset() {
        assertThatThrownBy(() -> service.search(consumer, TOPIC, request(SeekType.OFFSET)))
                .isInstanceOf(InvalidRecordRequestException.class);
    }

    @Test
    void unknownTopicIsNotFound() {
        assertThatThrownBy(() -> service.search(consumer, "missing", request(SeekType.NEWEST)))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void startOffsetsAreClampedToEachPartitionsRange() {
        Map<TopicPartition, Long> begin = Map.of(P0, 5L, P1, 0L);
        Map<TopicPartition, Long> end = Map.of(P0, 20L, P1, 3L);

        assertThat(TopicRecordService.startOffsets(SeekType.OFFSET, List.of(P0, P1), begin, end, Map.of(), 10L, 0))
                .isEqualTo(Map.of(P0, 10L, P1, 3L));
        assertThat(TopicRecordService.startOffsets(SeekType.OFFSET, List.of(P0), begin, end, Map.of(), 1L, 0))
                .isEqualTo(Map.of(P0, 5L));
        assertThat(TopicRecordService.startOffsets(SeekType.NEWEST, List.of(P0, P1), begin, end, Map.of(), null, 10))
                .isEqualTo(Map.of(P0, 10L, P1, 0L));
        // A partition with nothing at or after the timestamp starts at its end, i.e. returns nothing.
        assertThat(TopicRecordService.startOffsets(SeekType.TIMESTAMP, List.of(P0, P1), begin, end, Map.of(P0, 12L), null, 0))
                .isEqualTo(Map.of(P0, 12L, P1, 3L));
    }

    @Test
    void decodesKeysAndValuesWithTheChosenSerde() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(TOPIC, 0, 0, 1000L, TimestampType.CREATE_TIME,
                Integer.BYTES, 3, ByteBuffer.allocate(Integer.BYTES).putInt(42).array(), new byte[]{1, 2, (byte) 255},
                new RecordHeaders().add("trace", "abc".getBytes(StandardCharsets.UTF_8)), Optional.empty());

        TopicRecordDto dto = TopicRecordService.toDto(record, RecordSerde.INT, RecordSerde.HEX);

        assertThat(dto.getKey()).isEqualTo("42");
        assertThat(dto.getValue()).isEqualTo("0102ff");
        assertThat(dto.getKeySize()).isEqualTo(4);
        assertThat(dto.getTimestampType()).isEqualTo("CreateTime");
        assertThat(dto.getHeaders()).containsExactly(new TopicRecordDto.Header("trace", "abc"));
        assertThat(RecordSerde.LONG.decode(new byte[]{1, 2})).contains("not a 8-byte number");
        assertThat(RecordSerde.BASE64.decode(new byte[]{1, 2, 3})).isEqualTo("AQID");
        assertThat(RecordSerde.STRING.decode(null)).isNull();
    }

    private static TopicRecordSearchRequestDto request(SeekType seekType) {
        return new TopicRecordSearchRequestDto().setSeekType(seekType);
    }

    private static ConsumerRecord<byte[], byte[]> record(int partition, long offset, long timestamp, String key, String value) {
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        byte[] v = value.getBytes(StandardCharsets.UTF_8);
        return new ConsumerRecord<>(TOPIC, partition, offset, timestamp, TimestampType.CREATE_TIME, k.length, v.length,
                k, v, new RecordHeaders(), Optional.empty());
    }

}
