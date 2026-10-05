package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.BrokerOverviewDto;
import com.csl.kafkador.domain.dto.ClusterOverviewDto;
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
import static org.assertj.core.api.Assertions.within;

class ClusterOverviewCalculatorTest {

    private static final Node B1 = new Node(1, "kafka1", 29092);
    private static final Node B2 = new Node(2, "kafka2", 29092);
    private static final Node B3 = new Node(3, "kafka3", 29092);
    private static final List<Node> BROKERS = List.of(B3, B1, B2);

    @Test
    void countsPartitionHealthAcrossTopics() {
        TopicDescription orders = topic("orders",
                partition(0, B2, List.of(B1, B2, B3), List.of(B1, B2, B3)),
                partition(1, B2, List.of(B1, B2, B3), List.of(B2, B3)),        // B1 out of sync
                partition(2, Node.noNode(), List.of(B1, B2, B3), List.of()));  // offline

        ClusterOverviewDto overview = ClusterOverviewCalculator.calculate(BROKERS, List.of(orders), null);

        assertThat(overview.getBrokerCount()).isEqualTo(3);
        assertThat(overview.getPartitionCount()).isEqualTo(3);
        assertThat(overview.getOnlinePartitionCount()).isEqualTo(2);
        assertThat(overview.getUnderReplicatedPartitionCount()).isEqualTo(2);
        assertThat(overview.getReplicaCount()).isEqualTo(9);
        assertThat(overview.getInSyncReplicaCount()).isEqualTo(5);
        assertThat(overview.getOutOfSyncReplicaCount()).isEqualTo(4);
    }

    @Test
    void reportsPerBrokerDistributionAndSkewSortedById() {
        // Like the reference screen: every partition led by broker 2, all replicas in sync.
        TopicDescription orders = topic("orders",
                partition(0, B2, List.of(B1, B2, B3), List.of(B1, B2, B3)),
                partition(1, B2, List.of(B1, B2, B3), List.of(B1, B2, B3)),
                partition(2, B2, List.of(B1, B2, B3), List.of(B1, B2, B3)));

        List<BrokerOverviewDto> brokers = ClusterOverviewCalculator.calculate(BROKERS, List.of(orders), null).getBrokers();

        assertThat(brokers).extracting(BrokerOverviewDto::getId).containsExactly("1", "2", "3");
        BrokerOverviewDto b1 = brokers.get(0), b2 = brokers.get(1);
        assertThat(b1.getReplicaCount()).isEqualTo(3);
        assertThat(b1.getInSyncReplicaCount()).isEqualTo(3);
        assertThat(b1.getLeaderCount()).isZero();
        assertThat(b1.getReplicasSkew()).isCloseTo(0.0, within(0.001));
        assertThat(b1.getLeadersSkew()).isCloseTo(-100.0, within(0.001));
        assertThat(b2.getLeaderCount()).isEqualTo(3);
        assertThat(b2.getLeadersSkew()).isCloseTo(200.0, within(0.001));
        // No log dir data was available.
        assertThat(b1.getDiskUsageBytes()).isNull();
        assertThat(b1.getLogCount()).isNull();
    }

    @Test
    void skewIsUnknownForAnEmptyCluster() {
        BrokerOverviewDto b1 = ClusterOverviewCalculator.calculate(BROKERS, List.of(), null).getBrokers().get(0);

        assertThat(b1.getReplicasSkew()).isNull();
        assertThat(b1.getLeadersSkew()).isNull();
    }

    @Test
    void sumsDiskUsageAndLogCountPerBrokerSkippingFailedDirs() {
        LogDirDescription healthy = new LogDirDescription(null, Map.of(
                new TopicPartition("orders", 0), new ReplicaInfo(1000, 0, false),
                new TopicPartition("orders", 1), new ReplicaInfo(2500, 0, false)));
        LogDirDescription failed = new LogDirDescription(new org.apache.kafka.common.errors.KafkaStorageException("disk"),
                Map.of(new TopicPartition("orders", 2), new ReplicaInfo(99999, 0, false)));

        BrokerOverviewDto b1 = ClusterOverviewCalculator.calculate(BROKERS, List.of(),
                Map.of(1, Map.of("/data/a", healthy, "/data/b", failed))).getBrokers().get(0);

        assertThat(b1.getDiskUsageBytes()).isEqualTo(3500L);
        assertThat(b1.getLogCount()).isEqualTo(2);
    }

    private static TopicDescription topic(String name, TopicPartitionInfo... partitions) {
        return new TopicDescription(name, false, List.of(partitions));
    }

    private static TopicPartitionInfo partition(int id, Node leader, List<Node> replicas, List<Node> isr) {
        return new TopicPartitionInfo(id, leader, replicas, isr);
    }
}
