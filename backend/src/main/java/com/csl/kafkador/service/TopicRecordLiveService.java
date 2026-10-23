package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.RecordFilterDto;
import com.csl.kafkador.domain.dto.TopicRecordDto;
import com.csl.kafkador.domain.options.RecordDecoder;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.exception.TopicNotFoundException;
import com.csl.kafkador.service.registry.SchemaRegistryRecordDecoders;
import com.csl.kafkador.util.RecordFilterMatcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Live mode of the topic Records tab: streams records produced after the request started, as Server-Sent Events.
 * Like browsing it uses an assigned consumer with no group, so listening never commits offsets or joins a group.
 * Events: "ready" once positioned at the end, "records" with a JSON array per poll, "failure" with a message.
 */
@Slf4j
@Service("TopicRecordLiveService")
@RequiredArgsConstructor
public class TopicRecordLiveService {

    /** A comment is sent when nothing else has been, so a closed browser tab is noticed and its consumer closed. */
    static final long HEARTBEAT_MILLIS = 15_000;

    private final ConnectionService connectionService;
    private final SchemaRegistryRecordDecoders recordDecoders;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** What the streaming loop writes to; an SSE emitter in production. */
    interface LiveSink {
        void ready() throws IOException;

        void records(List<TopicRecordDto> records) throws IOException;

        void heartbeat() throws IOException;

        void failure(String message);
    }

    public SseEmitter listen(String topic, List<Integer> partitions, RecordSerde keySerde, RecordSerde valueSerde,
                             List<RecordFilterDto> filters) throws InvalidRecordRequestException {
        // Read in the request thread: the active connection lives in the HTTP session.
        Properties properties = consumerProperties(connectionService.getActiveConnectionProperties());
        String clusterId = connectionService.getActiveConnection().getClusterId();
        RecordDecoder keyDecoder = recordDecoders.decoder(keySerde, topic, clusterId);
        RecordDecoder valueDecoder = recordDecoders.decoder(valueSerde, topic, clusterId);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        AtomicBoolean running = new AtomicBoolean(true);
        emitter.onCompletion(() -> running.set(false));
        emitter.onError(e -> running.set(false));
        emitter.onTimeout(() -> running.set(false));

        executor.execute(() -> {
            try (Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
                stream(consumer, topic, partitions, keyDecoder, valueDecoder, filters, sink(emitter), running::get);
            } catch (Exception e) {
                log.error("Live stream of topic {} failed to start", topic, e);
                sink(emitter).failure("Live stream stopped: " + e.getMessage());
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    /** Positions at the end of the partitions and forwards matching records until {@code running} turns false. */
    void stream(Consumer<byte[], byte[]> consumer, String topic, List<Integer> partitions, RecordDecoder keyDecoder,
                RecordDecoder valueDecoder, List<RecordFilterDto> filters, LiveSink sink, BooleanSupplier running) {
        try {
            List<TopicPartition> assigned = TopicRecordService.resolvePartitions(consumer, topic, partitions);
            consumer.assign(assigned);
            consumer.seekToEnd(assigned);
            assigned.forEach(consumer::position); // resolve the end now, so only records produced from here on show
            sink.ready();
            long lastSent = System.currentTimeMillis();
            while (running.getAsBoolean()) {
                List<TopicRecordDto> batch = new ArrayList<>();
                for (ConsumerRecord<byte[], byte[]> record : consumer.poll(TopicRecordService.POLL_TIMEOUT)) {
                    TopicRecordDto dto = TopicRecordService.toDto(record, keyDecoder, valueDecoder);
                    if (RecordFilterMatcher.matches(dto, filters)) batch.add(dto);
                }
                long now = System.currentTimeMillis();
                if (!batch.isEmpty()) {
                    sink.records(batch);
                    lastSent = now;
                } else if (now - lastSent >= HEARTBEAT_MILLIS) {
                    sink.heartbeat();
                    lastSent = now;
                }
            }
        } catch (TopicNotFoundException | InvalidRecordRequestException e) {
            sink.failure(e.getMessage());
        } catch (IOException e) {
            log.debug("Live stream of topic {} closed by the client: {}", topic, e.getMessage());
        } catch (Exception e) {
            log.error("Live stream of topic {} failed", topic, e);
            sink.failure("Live stream stopped: " + e.getMessage());
        }
    }

    /** The filters query parameter: a JSON array of {field, operator, value}, held to the same limits as a search. */
    public static List<RecordFilterDto> parseFilters(String json) throws InvalidRecordRequestException {
        if (json == null || json.isBlank()) return List.of();
        List<RecordFilterDto> filters;
        try {
            filters = JSON.readValue(json, new TypeReference<List<RecordFilterDto>>() { });
        } catch (JsonProcessingException e) {
            throw new InvalidRecordRequestException("Filters must be a JSON array of {field, operator, value}");
        }
        if (filters.size() > 10) throw new InvalidRecordRequestException("At most 10 filters are allowed");
        for (RecordFilterDto filter : filters) {
            if (filter == null || filter.getField() == null || filter.getOperator() == null
                    || filter.getValue() == null || filter.getValue().isBlank() || filter.getValue().length() > 1000) {
                throw new InvalidRecordRequestException("Every filter needs a field, an operator and a value of up to 1000 characters");
            }
        }
        return filters;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static LiveSink sink(SseEmitter emitter) {
        return new LiveSink() {
            @Override
            public void ready() throws IOException {
                emitter.send(SseEmitter.event().name("ready").data(Map.of("ready", true), MediaType.APPLICATION_JSON));
            }

            @Override
            public void records(List<TopicRecordDto> records) throws IOException {
                emitter.send(SseEmitter.event().name("records").data(records, MediaType.APPLICATION_JSON));
            }

            @Override
            public void heartbeat() throws IOException {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            }

            @Override
            public void failure(String message) {
                try {
                    emitter.send(SseEmitter.event().name("failure").data(Map.of("message", String.valueOf(message)),
                            MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    log.debug("Could not report a live stream failure: {}", e.getMessage());
                }
            }
        };
    }

    private static Properties consumerProperties(Properties connection) {
        connection.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        connection.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        connection.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafkador-records-live");
        return connection;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("TopicRecordLiveService executor did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
