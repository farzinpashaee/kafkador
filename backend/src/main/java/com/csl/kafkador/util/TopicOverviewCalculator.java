package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.TopicOverviewDto;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the Topics page rows from plain Admin API results, so the counting can be tested without a cluster. */
public final class TopicOverviewCalculator {

    private TopicOverviewCalculator() {
    }

    /**
     * @param earliestOffsets / latestOffsets per partition; partitions missing from either make the count unknown
     * @param cleanupPolicies cleanup.policy per topic name, or null when topic configs couldn't be read
     * @param logDirs         describeLogDirs result per broker id, or null when it couldn't be read
     */
    public static List<TopicOverviewDto> calculate(Collection<TopicDescription> topics,
                                                   Map<TopicPartition, Long> earliestOffsets,
                                                   Map<TopicPartition, Long> latestOffsets,
                                                   Map<String, String> cleanupPolicies,
                                                   Map<Integer, Map<String, LogDirDescription>> logDirs) {
        Map<TopicPartition, Long> partitionSizes = replicaSizeTotals(logDirs);
        return topics.stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(topic -> {
                    String policy = cleanupPolicies == null ? null : cleanupPolicies.get(topic.name());
                    return new TopicOverviewDto()
                            .setName(topic.name())
                            .setId(topic.topicId() == null ? null : topic.topicId().toString())
                            .setInternal(topic.isInternal() || topic.name().startsWith("_"))
                            .setPartitionCount(topic.partitions().size())
                            .setReplicationFactor(topic.partitions().stream().mapToInt(p -> p.replicas().size()).max().orElse(0))
                            .setOutOfSyncReplicaCount(topic.partitions().stream().mapToInt(p -> p.replicas().size() - p.isr().size()).sum())
                            .setCleanupPolicy(policy)
                            .setMessageCount(isCompacted(policy) ? null : messageCount(topic, earliestOffsets, latestOffsets))
                            .setSizeBytes(logDirs == null ? null : sizeBytes(topic, partitionSizes));
                })
                .toList();
    }

    private static boolean isCompacted(String cleanupPolicy) {
        return cleanupPolicy != null && cleanupPolicy.contains("compact");
    }

    private static Long messageCount(TopicDescription topic, Map<TopicPartition, Long> earliest, Map<TopicPartition, Long> latest) {
        if (earliest == null || latest == null) return null;
        long total = 0;
        for (TopicPartitionInfo partition : topic.partitions()) {
            TopicPartition tp = new TopicPartition(topic.name(), partition.partition());
            Long start = earliest.get(tp), end = latest.get(tp);
            if (start == null || end == null) return null;
            total += Math.max(0, end - start);
        }
        return total;
    }

    private static long sizeBytes(TopicDescription topic, Map<TopicPartition, Long> partitionSizes) {
        return topic.partitions().stream()
                .mapToLong(p -> partitionSizes.getOrDefault(new TopicPartition(topic.name(), p.partition()), 0L))
                .sum();
    }

    private static Map<TopicPartition, Long> replicaSizeTotals(Map<Integer, Map<String, LogDirDescription>> logDirs) {
        Map<TopicPartition, Long> sizes = new HashMap<>();
        if (logDirs == null) return sizes;
        for (Map<String, LogDirDescription> dirs : logDirs.values()) {
            for (LogDirDescription dir : dirs.values()) {
                if (dir.error() != null) continue;
                for (Map.Entry<TopicPartition, ReplicaInfo> replica : dir.replicaInfos().entrySet()) {
                    sizes.merge(replica.getKey(), replica.getValue().size(), Long::sum);
                }
            }
        }
        return sizes;
    }

}
