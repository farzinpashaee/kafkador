package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.TopicOverviewDto;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicOverviewCalculatorTest {

    private static final Node B1 = new Node(1, "kafka1", 9092);
    private static final Node B2 = new Node(2, "kafka2", 9092);
    private static final Node B3 = new Node(3, "kafka3", 9092);

    @Test
    void countsReplicationHealthMessagesAndSizePerTopic() {
        TopicDescription orders = new TopicDescription("orders", false, List.of(
                new TopicPartitionInfo(0, B1, List.of(B1, B2, B3), List.of(B1, B2, B3)),
                new TopicPartitionInfo(1, B2, List.of(B1, B2, B3), List.of(B2))));       // 2 out of sync

        Map<TopicPartition, Long> earliest = Map.of(tp("orders", 0), 10L, tp("orders", 1), 0L);
        Map<TopicPartition, Long> latest = Map.of(tp("orders", 0), 25L, tp("orders", 1), 5L);
        // Partition 0 has replicas of 100 and 120 bytes, partition 1 one of 50: every replica counts.
        Map<Integer, Map<String, LogDirDescription>> logDirs = Map.of(
                1, Map.of("/d", dir(Map.of(tp("orders", 0), 100L, tp("orders", 1), 50L))),
                2, Map.of("/d", dir(Map.of(tp("orders", 0), 120L))));

        TopicOverviewDto row = TopicOverviewCalculator.calculate(List.of(orders), earliest, latest,
                Map.of("orders", "delete"), logDirs).get(0);

        assertThat(row.getName()).isEqualTo("orders");
        assertThat(row.getInternal()).isFalse();
        assertThat(row.getPartitionCount()).isEqualTo(2);
        assertThat(row.getReplicationFactor()).isEqualTo(3);
        assertThat(row.getOutOfSyncReplicaCount()).isEqualTo(2);
        assertThat(row.getMessageCount()).isEqualTo(20L);
        assertThat(row.getSizeBytes()).isEqualTo(270L);
    }

    @Test
    void treatsKafkaInternalAndUnderscoreTopicsAsInternalAndSortsByName() {
        List<TopicOverviewDto> rows = TopicOverviewCalculator.calculate(List.of(
                new TopicDescription("orders", false, List.of()),
                new TopicDescription("_schemas", false, List.of()),
                new TopicDescription("__consumer_offsets", true, List.of())), Map.of(), Map.of(), Map.of(), Map.of());

        assertThat(rows).extracting(TopicOverviewDto::getName).containsExactly("__consumer_offsets", "_schemas", "orders");
        assertThat(rows).extracting(TopicOverviewDto::getInternal).containsExactly(true, true, false);
    }

    @Test
    void leavesMessageCountUnknownForCompactedTopicsAndMissingOffsets() {
        TopicDescription compacted = new TopicDescription("_schemas", false,
                List.of(new TopicPartitionInfo(0, B1, List.of(B1), List.of(B1))));
        TopicDescription leaderless = new TopicDescription("orders", false,
                List.of(new TopicPartitionInfo(0, Node.noNode(), List.of(B1), List.of())));
        Map<TopicPartition, Long> offsets = Map.of(tp("_schemas", 0), 0L);

        List<TopicOverviewDto> rows = TopicOverviewCalculator.calculate(List.of(compacted, leaderless), offsets, offsets,
                Map.of("_schemas", "compact", "orders", "delete"), null);

        assertThat(rows).extracting(TopicOverviewDto::getMessageCount).containsOnlyNulls();
        // No log dir data at all: size is unknown rather than zero.
        assertThat(rows).extracting(TopicOverviewDto::getSizeBytes).containsOnlyNulls();
    }

    private static TopicPartition tp(String topic, int partition) {
        return new TopicPartition(topic, partition);
    }

    private static LogDirDescription dir(Map<TopicPartition, Long> sizes) {
        Map<TopicPartition, ReplicaInfo> replicas = new java.util.HashMap<>();
        sizes.forEach((tp, size) -> replicas.put(tp, new ReplicaInfo(size, 0, false)));
        return new LogDirDescription(null, replicas);
    }
}
