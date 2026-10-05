package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.BrokerOverviewDto;
import com.csl.kafkador.domain.dto.ClusterOverviewDto;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives partition health and the per-broker replica/leader distribution from plain Admin API results, so the
 * counting can be tested without a cluster.
 */
public final class ClusterOverviewCalculator {

    private ClusterOverviewCalculator() {
    }

    /**
     * @param logDirs describeLogDirs result per broker id, or null when it couldn't be read
     */
    public static ClusterOverviewDto calculate(Collection<Node> brokers,
                                               Collection<TopicDescription> topics,
                                               Map<Integer, Map<String, LogDirDescription>> logDirs) {
        Map<Integer, Integer> replicas = new HashMap<>();
        Map<Integer, Integer> inSync = new HashMap<>();
        Map<Integer, Integer> leaders = new HashMap<>();
        int partitions = 0, online = 0, underReplicated = 0, replicaTotal = 0, inSyncTotal = 0;

        for (TopicDescription topic : topics) {
            for (TopicPartitionInfo partition : topic.partitions()) {
                partitions++;
                Node leader = partition.leader();
                if (leader != null && !leader.isEmpty()) {
                    online++;
                    leaders.merge(leader.id(), 1, Integer::sum);
                }
                if (partition.isr().size() < partition.replicas().size()) underReplicated++;
                replicaTotal += partition.replicas().size();
                inSyncTotal += partition.isr().size();
                partition.replicas().forEach(node -> replicas.merge(node.id(), 1, Integer::sum));
                partition.isr().forEach(node -> inSync.merge(node.id(), 1, Integer::sum));
            }
        }

        int leaderTotal = online;
        List<BrokerOverviewDto> brokerRows = brokers.stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(node -> {
                    BrokerOverviewDto row = new BrokerOverviewDto()
                            .setId(node.idString())
                            .setHost(node.host())
                            .setPort(node.port())
                            .setRack(node.rack())
                            .setActiveController(false)
                            .setReplicaCount(replicas.getOrDefault(node.id(), 0))
                            .setInSyncReplicaCount(inSync.getOrDefault(node.id(), 0))
                            .setLeaderCount(leaders.getOrDefault(node.id(), 0));
                    row.setReplicasSkew(skew(row.getReplicaCount(), replicas.values().stream().mapToInt(Integer::intValue).sum(), brokers.size()));
                    row.setLeadersSkew(skew(row.getLeaderCount(), leaderTotal, brokers.size()));
                    applyLogDirs(row, logDirs == null ? null : logDirs.get(node.id()));
                    return row;
                })
                .toList();

        return new ClusterOverviewDto()
                .setBrokerCount(brokers.size())
                .setPartitionCount(partitions)
                .setOnlinePartitionCount(online)
                .setUnderReplicatedPartitionCount(underReplicated)
                .setReplicaCount(replicaTotal)
                .setInSyncReplicaCount(inSyncTotal)
                .setOutOfSyncReplicaCount(replicaTotal - inSyncTotal)
                .setBrokers(brokerRows);
    }

    /** How far {@code count} is from an even share of {@code total} over {@code brokerCount} brokers, in percent. */
    static Double skew(int count, int total, int brokerCount) {
        if (total == 0 || brokerCount == 0) return null;
        double evenShare = (double) total / brokerCount;
        return (count - evenShare) / evenShare * 100;
    }

    private static void applyLogDirs(BrokerOverviewDto row, Map<String, LogDirDescription> dirs) {
        if (dirs == null) return;
        long bytes = 0;
        int logs = 0;
        for (LogDirDescription dir : dirs.values()) {
            if (dir.error() != null) continue;
            for (ReplicaInfo replica : dir.replicaInfos().values()) {
                bytes += replica.size();
                logs++;
            }
        }
        row.setDiskUsageBytes(bytes).setLogCount(logs);
    }

}
