package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.TopicRecordProduceRequestDto;
import com.csl.kafkador.domain.dto.TopicRecordProduceResultDto;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.exception.TopicNotFoundException;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TopicRecordProducerServiceTest {

    private static final Node BROKER = new Node(1, "kafka1", 9092);
    private static final Cluster CLUSTER = new Cluster("c1", List.of(BROKER), List.of(
            new PartitionInfo("orders", 0, BROKER, new Node[]{BROKER}, new Node[]{BROKER}),
            new PartitionInfo("orders", 1, BROKER, new Node[]{BROKER}, new Node[]{BROKER})), Set.of(), Set.of());

    private final TopicRecordProducerService service = new TopicRecordProducerService(mock(ConnectionService.class));
    private final MockProducer<byte[], byte[]> producer =
            new MockProducer<>(CLUSTER, true, null, new ByteArraySerializer(), new ByteArraySerializer());

    @Test
    void producesToTheChosenPartitionWithEncodedKeyValueAndHeaders() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("trace-id", "abc");
        TopicRecordProduceRequestDto request = new TopicRecordProduceRequestDto()
                .setPartition(1).setKey("42").setKeySerde(RecordSerde.INT)
                .setValue("{\"orderId\": 42}").setValueSerde(RecordSerde.JSON).setHeaders(headers);

        TopicRecordProduceResultDto result = service.produce(producer, TopicRecordProducerService.toRecord("orders", request));

        assertThat(result.partition()).isEqualTo(1);
        ProducerRecord<byte[], byte[]> sent = producer.history().get(0);
        assertThat(sent.key()).containsExactly(0, 0, 0, 42);
        assertThat(new String(sent.value(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\": 42}");
        assertThat(sent.headers().lastHeader("trace-id").value()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void nullKeyAndValueAreSentAsNull() throws Exception {
        ProducerRecord<byte[], byte[]> record = TopicRecordProducerService.toRecord("orders", new TopicRecordProduceRequestDto());

        service.produce(producer, record);

        assertThat(producer.history().get(0).key()).isNull();
        assertThat(producer.history().get(0).value()).isNull();
    }

    @Test
    void rejectsAPartitionTheTopicDoesNotHave() {
        assertThatThrownBy(() -> service.produce(producer,
                TopicRecordProducerService.toRecord("orders", new TopicRecordProduceRequestDto().setPartition(5).setValue("x"))))
                .isInstanceOf(InvalidRecordRequestException.class)
                .hasMessageContaining("no partition 5");
        assertThat(producer.history()).isEmpty();
    }

    @Test
    void unknownTopicIsNotFound() {
        assertThatThrownBy(() -> service.produce(producer,
                TopicRecordProducerService.toRecord("missing", new TopicRecordProduceRequestDto().setValue("x"))))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void textThatDoesNotFitTheSerdeIsABadRequest() {
        assertThatThrownBy(() -> TopicRecordProducerService.toRecord("orders",
                new TopicRecordProduceRequestDto().setValue("{oops").setValueSerde(RecordSerde.JSON)))
                .isInstanceOf(InvalidRecordRequestException.class)
                .hasMessageStartingWith("Value: Not valid JSON");
        assertThatThrownBy(() -> TopicRecordProducerService.toRecord("orders",
                new TopicRecordProduceRequestDto().setKey("12.5").setKeySerde(RecordSerde.LONG)))
                .isInstanceOf(InvalidRecordRequestException.class)
                .hasMessage("Key: '12.5' is not a valid long");
    }

    @Test
    void everySerdeRoundTrips() {
        Map<RecordSerde, String> samples = Map.of(
                RecordSerde.STRING, "héllo", RecordSerde.JSON, "{\"a\":[1,2]}", RecordSerde.INT, "-7",
                RecordSerde.LONG, "9223372036854775807", RecordSerde.FLOAT, "1.5", RecordSerde.DOUBLE, "2.25",
                RecordSerde.BASE64, "AQID", RecordSerde.HEX, "0102ff");
        samples.forEach((serde, text) -> assertThat(serde.decode(serde.encode(text))).as(serde.name()).isEqualTo(text));
        assertThat(RecordSerde.HEX.encode("0x01 02")).containsExactly(1, 2);
    }

}
