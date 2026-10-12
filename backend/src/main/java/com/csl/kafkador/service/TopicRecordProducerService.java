package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.TopicRecordProduceRequestDto;
import com.csl.kafkador.domain.dto.TopicRecordProduceResultDto;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.TopicNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Produces single records from the Records tab, encoding the key and value with the chosen serdes. */
@Slf4j
@Service("TopicRecordProducerService")
@RequiredArgsConstructor
public class TopicRecordProducerService {

    static final long SEND_TIMEOUT_SECONDS = 30;
    /** How long metadata lookups may block, e.g. for a topic that does not exist. */
    static final String MAX_BLOCK_MS = "10000";

    private final ConnectionService connectionService;

    public TopicRecordProduceResultDto produce(String topic, TopicRecordProduceRequestDto request)
            throws KafkaAdminApiException, TopicNotFoundException, InvalidRecordRequestException {
        ProducerRecord<byte[], byte[]> record = toRecord(topic, request);
        try (Producer<byte[], byte[]> producer = createProducer()) {
            return produce(producer, record);
        } catch (ConnectionSessionExpiredException | TopicNotFoundException | InvalidRecordRequestException e) {
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("Failed to produce to topic {}", topic, cause);
            throw new KafkaAdminApiException("Error producing the record: " + cause.getMessage());
        } catch (Exception e) {
            log.error("Failed to produce to topic {}", topic, e);
            throw new KafkaAdminApiException("Error producing the record: " + e.getMessage());
        }
    }

    TopicRecordProduceResultDto produce(Producer<byte[], byte[]> producer, ProducerRecord<byte[], byte[]> record)
            throws Exception {
        List<PartitionInfo> partitions;
        try {
            partitions = producer.partitionsFor(record.topic());
        } catch (TimeoutException e) {
            throw new TopicNotFoundException("Topic '" + record.topic() + "' does not exist");
        }
        if (partitions == null || partitions.isEmpty()) {
            throw new TopicNotFoundException("Topic '" + record.topic() + "' does not exist");
        }
        if (record.partition() != null && partitions.stream().noneMatch(p -> p.partition() == record.partition())) {
            throw new InvalidRecordRequestException("Topic '" + record.topic() + "' has no partition " + record.partition());
        }
        RecordMetadata metadata = producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new TopicRecordProduceResultDto(metadata.partition(), metadata.offset(), metadata.timestamp());
    }

    /** Encodes the request; text that does not fit its serde is a bad request, not a server error. */
    static ProducerRecord<byte[], byte[]> toRecord(String topic, TopicRecordProduceRequestDto request)
            throws InvalidRecordRequestException {
        byte[] key = encode(request.getKeySerde(), request.getKey(), "Key");
        byte[] value = encode(request.getValueSerde(), request.getValue(), "Value");
        List<Header> headers = request.getHeaders() == null ? List.of() : request.getHeaders().entrySet().stream()
                .<Header>map(h -> new RecordHeader(h.getKey(),
                        h.getValue() == null ? null : h.getValue().getBytes(StandardCharsets.UTF_8)))
                .toList();
        return new ProducerRecord<>(topic, request.getPartition(), key, value, headers);
    }

    private static byte[] encode(RecordSerde serde, String text, String what) throws InvalidRecordRequestException {
        try {
            return serde.encode(text);
        } catch (IllegalArgumentException e) {
            throw new InvalidRecordRequestException(what + ": " + e.getMessage());
        }
    }

    private Producer<byte[], byte[]> createProducer() {
        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkador-records-producer");
        return new KafkaProducer<>(properties, new ByteArraySerializer(), new ByteArraySerializer());
    }

}
