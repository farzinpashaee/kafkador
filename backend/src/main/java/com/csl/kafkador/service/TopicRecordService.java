package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.TopicRecordDto;
import com.csl.kafkador.domain.dto.TopicRecordSearchRequestDto;
import com.csl.kafkador.domain.dto.TopicRecordSearchRequestDto.SeekType;
import com.csl.kafkador.domain.dto.TopicRecordSearchResultDto;
import com.csl.kafkador.domain.options.RecordDecoder;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.TopicNotFoundException;
import com.csl.kafkador.service.registry.SchemaRegistryRecordDecoders;
import com.csl.kafkador.util.RecordFilterMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Reads records for the topic Records tab. Uses an assigned consumer with no group, so browsing never
 * commits offsets or joins a group, and stops at a record and time budget so a huge topic can't hang a request.
 */
@Slf4j
@Service("TopicRecordService")
@RequiredArgsConstructor
public class TopicRecordService {

    /** Most records read per request, filtered-out ones included. */
    static final int MAX_SCAN = 10_000;
    static final Duration MAX_DURATION = Duration.ofSeconds(10);
    static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    /** Longer keys/values are cut so one huge record can't bloat the response; sizes still report the full length. */
    static final int MAX_TEXT_LENGTH = 100_000;

    private final ConnectionService connectionService;
    private final SchemaRegistryRecordDecoders recordDecoders;

    public TopicRecordSearchResultDto search(String topic, TopicRecordSearchRequestDto request)
            throws KafkaAdminApiException, TopicNotFoundException, InvalidRecordRequestException {
        String clusterId = connectionService.getActiveConnection().getClusterId();
        RecordDecoder keyDecoder = recordDecoders.decoder(request.getKeySerde(), topic, clusterId);
        RecordDecoder valueDecoder = recordDecoders.decoder(request.getValueSerde(), topic, clusterId);
        try (Consumer<byte[], byte[]> consumer = createConsumer()) {
            return search(consumer, topic, request, keyDecoder, valueDecoder);
        } catch (ConnectionSessionExpiredException | TopicNotFoundException | InvalidRecordRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read records of topic {}", topic, e);
            throw new KafkaAdminApiException("Error reading records: " + e.getMessage());
        }
    }

    /** A search decoding with the request's serdes as they are; Schema Registry ones only describe their bytes. */
    TopicRecordSearchResultDto search(Consumer<byte[], byte[]> consumer, String topic, TopicRecordSearchRequestDto request)
            throws TopicNotFoundException, InvalidRecordRequestException {
        return search(consumer, topic, request, request.getKeySerde(), request.getValueSerde());
    }

    TopicRecordSearchResultDto search(Consumer<byte[], byte[]> consumer, String topic, TopicRecordSearchRequestDto request,
                                      RecordDecoder keyDecoder, RecordDecoder valueDecoder)
            throws TopicNotFoundException, InvalidRecordRequestException {
        if (request.getSeekType() == SeekType.OFFSET && request.getOffset() == null) {
            throw new InvalidRecordRequestException("An offset is required to read from an offset");
        }
        if (request.getSeekType() == SeekType.TIMESTAMP && request.getTimestamp() == null) {
            throw new InvalidRecordRequestException("A timestamp is required to read from a point in time");
        }
        List<TopicPartition> partitions = resolvePartitions(consumer, topic, request.getPartitions());

        Map<TopicPartition, Long> begin = consumer.beginningOffsets(partitions, MAX_DURATION);
        Map<TopicPartition, Long> end = consumer.endOffsets(partitions, MAX_DURATION);
        Map<TopicPartition, Long> fromTimestamp = request.getSeekType() == SeekType.TIMESTAMP
                ? timestampOffsets(consumer, partitions, request.getTimestamp()) : Map.of();

        boolean filtered = !request.getFilters().isEmpty();
        int newestWindow = filtered ? Math.max(request.getLimit(), MAX_SCAN / partitions.size()) : request.getLimit();
        Map<TopicPartition, Long> start = startOffsets(request.getSeekType(), partitions, begin, end, fromTimestamp,
                request.getOffset(), newestWindow);

        Scan scan = scan(consumer, start, end, request, keyDecoder, valueDecoder, request.getSeekType() != SeekType.NEWEST);
        boolean newestCut = request.getSeekType() == SeekType.NEWEST && filtered && scan.matches.size() < request.getLimit()
                && partitions.stream().anyMatch(tp -> start.get(tp) > begin.get(tp));
        return new TopicRecordSearchResultDto()
                .setRecords(select(scan.matches, request.getSeekType(), request.getLimit()))
                .setScannedCount(scan.scanned)
                .setScanLimitReached(scan.budgetExhausted || newestCut);
    }

    /** The requested partitions of the topic, or all of them when none are requested. */
    static List<TopicPartition> resolvePartitions(Consumer<byte[], byte[]> consumer, String topic, List<Integer> requested)
            throws TopicNotFoundException, InvalidRecordRequestException {
        List<PartitionInfo> infos = consumer.partitionsFor(topic, MAX_DURATION);
        if (infos == null || infos.isEmpty()) {
            throw new TopicNotFoundException("Topic '" + topic + "' does not exist");
        }
        Set<Integer> existing = infos.stream().map(PartitionInfo::partition).collect(Collectors.toSet());
        List<Integer> unknown = requested.stream().filter(p -> !existing.contains(p)).toList();
        if (!unknown.isEmpty()) {
            throw new InvalidRecordRequestException("Topic '" + topic + "' has no partition " + unknown.get(0));
        }
        return (requested.isEmpty() ? existing : new HashSet<>(requested))
                .stream().sorted().map(p -> new TopicPartition(topic, p)).toList();
    }

    /**
     * Where to start reading each partition. NEWEST backs off {@code newestWindow} records from the end;
     * OFFSET is clamped to the partition's range; TIMESTAMP partitions with nothing that recent start at the end.
     */
    static Map<TopicPartition, Long> startOffsets(SeekType seekType, List<TopicPartition> partitions,
                                                  Map<TopicPartition, Long> begin, Map<TopicPartition, Long> end,
                                                  Map<TopicPartition, Long> fromTimestamp, Long offset, int newestWindow) {
        Map<TopicPartition, Long> start = new HashMap<>();
        for (TopicPartition tp : partitions) {
            long first = begin.getOrDefault(tp, 0L);
            long last = end.getOrDefault(tp, first);
            long position = switch (seekType) {
                case NEWEST -> Math.max(first, last - newestWindow);
                case OLDEST -> first;
                case OFFSET -> Math.min(Math.max(offset == null ? 0 : offset, first), last);
                case TIMESTAMP -> fromTimestamp.getOrDefault(tp, last);
            };
            start.put(tp, position);
        }
        return start;
    }

    /** Newest first for NEWEST, otherwise oldest first; then the first {@code limit}. */
    static List<TopicRecordDto> select(List<TopicRecordDto> matches, SeekType seekType, int limit) {
        Comparator<TopicRecordDto> order = Comparator.comparingLong(TopicRecordDto::getTimestamp)
                .thenComparingInt(TopicRecordDto::getPartition)
                .thenComparingLong(TopicRecordDto::getOffset);
        if (seekType == SeekType.NEWEST) order = order.reversed();
        return matches.stream().sorted(order).limit(limit).toList();
    }

    static TopicRecordDto toDto(ConsumerRecord<byte[], byte[]> record, RecordDecoder keyDecoder, RecordDecoder valueDecoder) {
        List<TopicRecordDto.Header> headers = StreamSupport.stream(record.headers().spliterator(), false)
                .map(TopicRecordService::header)
                .toList();
        return new TopicRecordDto()
                .setPartition(record.partition())
                .setOffset(record.offset())
                .setTimestamp(record.timestamp())
                .setTimestampType(record.timestampType() == null ? null : record.timestampType().name)
                .setKey(truncate(keyDecoder.decode(record.key())))
                .setValue(truncate(valueDecoder.decode(record.value())))
                .setKeySize(record.key() == null ? -1 : record.key().length)
                .setValueSize(record.value() == null ? -1 : record.value().length)
                .setHeaders(headers);
    }

    private record Scan(List<TopicRecordDto> matches, long scanned, boolean budgetExhausted) {
    }

    /**
     * Reads every partition from its start up to the end offset seen when the request began. Records produced
     * meanwhile are left for the next refresh. {@code stopAtLimit} ends the scan early once enough records match.
     */
    private Scan scan(Consumer<byte[], byte[]> consumer, Map<TopicPartition, Long> start, Map<TopicPartition, Long> end,
                      TopicRecordSearchRequestDto request, RecordDecoder keyDecoder, RecordDecoder valueDecoder,
                      boolean stopAtLimit) {
        Set<TopicPartition> active = start.entrySet().stream()
                .filter(e -> e.getValue() < end.getOrDefault(e.getKey(), 0L))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
        List<TopicRecordDto> matches = new ArrayList<>();
        if (active.isEmpty()) return new Scan(matches, 0, false);

        consumer.assign(active);
        active.forEach(tp -> consumer.seek(tp, start.get(tp)));
        long deadline = System.nanoTime() + MAX_DURATION.toNanos();
        long scanned = 0;
        while (!active.isEmpty()) {
            if (scanned >= MAX_SCAN || System.nanoTime() > deadline) {
                return new Scan(matches, scanned, true);
            }
            for (ConsumerRecord<byte[], byte[]> record : consumer.poll(POLL_TIMEOUT)) {
                TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                if (!active.contains(tp) || record.offset() >= end.get(tp)) continue;
                scanned++;
                TopicRecordDto dto = toDto(record, keyDecoder, valueDecoder);
                if (RecordFilterMatcher.matches(dto, request.getFilters())) matches.add(dto);
            }
            // Compaction gaps and transaction markers mean the last offset may never arrive as a record.
            Set<TopicPartition> done = active.stream()
                    .filter(tp -> consumer.position(tp) >= end.get(tp))
                    .collect(Collectors.toSet());
            if (!done.isEmpty()) {
                consumer.pause(done);
                active.removeAll(done);
            }
            if (stopAtLimit && matches.size() >= request.getLimit()) break;
        }
        return new Scan(matches, scanned, false);
    }

    private static Map<TopicPartition, Long> timestampOffsets(Consumer<byte[], byte[]> consumer, List<TopicPartition> partitions,
                                                              Long timestamp) {
        Map<TopicPartition, Long> query = partitions.stream().collect(Collectors.toMap(tp -> tp, tp -> timestamp));
        Map<TopicPartition, Long> offsets = new HashMap<>();
        consumer.offsetsForTimes(query, MAX_DURATION).forEach((tp, found) -> {
            if (found != null) offsets.put(tp, found.offset());
        });
        return offsets;
    }

    private static TopicRecordDto.Header header(Header header) {
        return new TopicRecordDto.Header(header.key(),
                header.value() == null ? null : truncate(new String(header.value(), StandardCharsets.UTF_8)));
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
    }

    private Consumer<byte[], byte[]> createConsumer() {
        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafkador-records-browser");
        return new KafkaConsumer<>(properties, new ByteArrayDeserializer(), new ByteArrayDeserializer());
    }

}
