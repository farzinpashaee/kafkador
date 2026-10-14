package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.EntityMetricsDto;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Point;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Series;
import com.csl.kafkador.domain.dto.EntityMetricsDto.Source;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.util.metrics.ConfluentMetricsDecoder;
import com.csl.kafkador.util.metrics.MetricSample;
import com.csl.kafkador.util.metrics.TelemetryMetricsDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Metrics tabs of the broker and topic pages. Confluent Platform can publish broker metrics to Kafka itself
 * ({@code _confluent-telemetry-metrics} by the Telemetry Reporter, {@code _confluent-metrics} by the Metrics
 * Reporter); this reads the newest records of whichever exist and keeps the samples about one broker or topic.
 * Like the Records tab it uses an assigned consumer with no group, within a record and time budget.
 */
@Slf4j
@Service("ConfluentMetricsService")
@RequiredArgsConstructor
public class ConfluentMetricsService {

    public static final String TELEMETRY_TOPIC = "_confluent-telemetry-metrics";
    public static final String METRICS_TOPIC = "_confluent-metrics";

    /** Most records read from the end of each metrics topic. */
    static final int MAX_SCAN = 20_000;
    static final Duration MAX_DURATION = Duration.ofSeconds(8);
    static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    static final int MAX_POINTS = 60;
    static final int MAX_SERIES = 500;

    public enum Scope { BROKER, TOPIC }

    private static final Map<String, BiFunction<byte[], Long, List<MetricSample>>> DECODERS = new LinkedHashMap<>();
    static {
        DECODERS.put(TELEMETRY_TOPIC, TelemetryMetricsDecoder::decode);
        DECODERS.put(METRICS_TOPIC, ConfluentMetricsDecoder::decode);
    }

    private final ConnectionService connectionService;

    public EntityMetricsDto getMetrics(Scope scope, String target) throws KafkaAdminApiException {
        try (Consumer<byte[], byte[]> consumer = createConsumer()) {
            return read(consumer, scope, target);
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read metrics of {} {}", scope, target, e);
            throw new KafkaAdminApiException("Error reading metrics: " + e.getMessage());
        }
    }

    EntityMetricsDto read(Consumer<byte[], byte[]> consumer, Scope scope, String target) {
        Map<String, List<PartitionInfo>> topics = consumer.listTopics(MAX_DURATION);
        EntityMetricsDto result = new EntityMetricsDto();
        List<Tagged> matches = new ArrayList<>();
        for (Map.Entry<String, BiFunction<byte[], Long, List<MetricSample>>> decoder : DECODERS.entrySet()) {
            String topic = decoder.getKey();
            Source source = new Source().setTopic(topic).setExists(topics.containsKey(topic));
            result.getSources().add(source);
            if (!source.isExists()) continue;
            try {
                scan(consumer, topic, topics.get(topic), decoder.getValue(), source, scope, target, matches);
            } catch (Exception e) {
                log.warn("Failed to read metrics topic {}", topic, e);
                source.setError(e.getMessage());
            }
        }
        List<Series> series = aggregate(matches);
        result.setSeriesLimitReached(series.size() > MAX_SERIES);
        result.setSeries(series.size() > MAX_SERIES ? new ArrayList<>(series.subList(0, MAX_SERIES)) : series);
        return result;
    }

    /** Reads up to {@link #MAX_SCAN} of the newest records of one metrics topic, spread over its partitions. */
    static void scan(Consumer<byte[], byte[]> consumer, String topic, List<PartitionInfo> infos,
                     BiFunction<byte[], Long, List<MetricSample>> decoder, Source source,
                     Scope scope, String target, List<Tagged> matches) {
        List<TopicPartition> partitions = infos.stream().map(p -> new TopicPartition(topic, p.partition())).toList();
        if (partitions.isEmpty()) return;
        Map<TopicPartition, Long> begin = consumer.beginningOffsets(partitions, MAX_DURATION);
        Map<TopicPartition, Long> end = consumer.endOffsets(partitions, MAX_DURATION);
        int window = Math.max(1, MAX_SCAN / partitions.size());

        consumer.assign(partitions);
        Set<TopicPartition> active = new HashSet<>();
        for (TopicPartition tp : partitions) {
            long last = end.getOrDefault(tp, 0L);
            long start = Math.max(begin.getOrDefault(tp, 0L), last - window);
            if (start < last) {
                consumer.seek(tp, start);
                active.add(tp);
            }
        }
        consumer.pause(partitions.stream().filter(tp -> !active.contains(tp)).toList());

        long deadline = System.nanoTime() + MAX_DURATION.toNanos();
        int scanned = 0;
        int undecoded = 0;
        int matched = 0;
        Long from = null;
        Long to = null;
        while (!active.isEmpty() && scanned < MAX_SCAN && System.nanoTime() < deadline) {
            for (ConsumerRecord<byte[], byte[]> record : consumer.poll(POLL_TIMEOUT)) {
                TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                if (!active.contains(tp) || record.offset() >= end.get(tp)) continue;
                scanned++;
                from = from == null ? record.timestamp() : Math.min(from, record.timestamp());
                to = to == null ? record.timestamp() : Math.max(to, record.timestamp());
                List<MetricSample> samples = record.value() == null ? List.of() : decoder.apply(record.value(), record.timestamp());
                if (samples.isEmpty()) undecoded++;
                for (MetricSample sample : samples) {
                    if (matches(scope, target, sample)) {
                        matches.add(new Tagged(topic, sample));
                        matched++;
                    }
                }
            }
            List<TopicPartition> done = active.stream().filter(tp -> consumer.position(tp) >= end.get(tp)).toList();
            consumer.pause(done);
            done.forEach(active::remove);
        }
        source.setScannedRecords(scanned).setUndecodedRecords(undecoded).setMatchedSamples(matched)
                .setFromTimestamp(from).setToTimestamp(to);
    }

    /** A broker's own metrics, not the per-topic ones it also reports; or a topic's, from every broker. */
    static boolean matches(Scope scope, String target, MetricSample sample) {
        return switch (scope) {
            case BROKER -> target.equals(sample.broker()) && sample.topic() == null;
            case TOPIC -> target.equals(sample.topic());
        };
    }

    /** Groups samples into one series per source, metric, broker and labels, keeping the newest points. */
    static List<Series> aggregate(List<Tagged> matches) {
        Map<String, Series> byKey = new LinkedHashMap<>();
        Map<String, TreeMap<Long, Double>> points = new LinkedHashMap<>();
        for (Tagged tagged : matches) {
            MetricSample sample = tagged.sample();
            Map<String, String> labels = new TreeMap<>(sample.labels());
            String key = String.join("\u0000", tagged.source(), sample.name(), sample.kind(),
                    String.valueOf(sample.broker()), labels.toString());
            byKey.computeIfAbsent(key, k -> new Series()
                    .setName(sample.name())
                    .setSource(tagged.source())
                    .setKind(sample.kind())
                    .setBroker(sample.broker())
                    .setLabels(labels));
            points.computeIfAbsent(key, k -> new TreeMap<>()).put(sample.timestamp(), sample.value());
        }
        List<Series> series = new ArrayList<>();
        for (Map.Entry<String, Series> entry : byKey.entrySet()) {
            List<Map.Entry<Long, Double>> all = new ArrayList<>(points.get(entry.getKey()).entrySet());
            List<Point> recent = all.subList(Math.max(0, all.size() - MAX_POINTS), all.size()).stream()
                    .map(p -> new Point(p.getKey(), p.getValue())).toList();
            Point latest = recent.get(recent.size() - 1);
            series.add(entry.getValue().setPoints(new ArrayList<>(recent)).setLatest(latest.v()).setLatestTimestamp(latest.t()));
        }
        series.sort(Comparator.comparing(Series::getName)
                .thenComparing(s -> s.getLabels().toString())
                .thenComparing(s -> String.valueOf(s.getBroker()))
                .thenComparing(Series::getSource));
        return series;
    }

    record Tagged(String source, MetricSample sample) {}

    private Consumer<byte[], byte[]> createConsumer() {
        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafkador-metrics-reader");
        return new KafkaConsumer<>(properties, new ByteArrayDeserializer(), new ByteArrayDeserializer());
    }

}
