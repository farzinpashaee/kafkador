package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.ConsumerGroupDetailsDto;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.GroupType;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerGroupDetailsCalculatorTest {

    private static final Node COORDINATOR = new Node(2, "kafka2", 9092);

    @Test
    void sumsLagPerTopicAndForTheGroup() {
        MemberDescription member = new MemberDescription("consumer-1", "client-1", "/10.0.0.1",
                new MemberAssignment(Set.of(tp("orders", 0), tp("orders", 1))));
        ConsumerGroupDescription group = group(GroupState.STABLE, List.of(member));

        Map<TopicPartition, OffsetAndMetadata> committed = Map.of(
                tp("orders", 0), new OffsetAndMetadata(90),
                tp("orders", 1), new OffsetAndMetadata(40),
                tp("payments", 0), new OffsetAndMetadata(5));
        Map<TopicPartition, Long> end = Map.of(tp("orders", 0), 100L, tp("orders", 1), 40L, tp("payments", 0), 8L);

        ConsumerGroupDetailsDto details = ConsumerGroupDetailsCalculator.calculate(group, committed, end);

        assertThat(details.getState()).isEqualTo("Stable");
        assertThat(details.getCoordinatorId()).isEqualTo(2);
        assertThat(details.getMemberCount()).isEqualTo(1);
        assertThat(details.getTopicCount()).isEqualTo(2);
        assertThat(details.getPartitionCount()).isEqualTo(3);
        assertThat(details.getTotalLag()).isEqualTo(13L);

        ConsumerGroupDetailsDto.Topic orders = details.getTopics().get(0);
        assertThat(orders.getName()).isEqualTo("orders");
        assertThat(orders.getLag()).isEqualTo(10L);
        assertThat(orders.getMemberCount()).isEqualTo(1);
        assertThat(orders.getPartitions()).extracting(ConsumerGroupDetailsDto.Partition::getPartition).containsExactly(0, 1);
        assertThat(orders.getPartitions().get(0).getConsumerId()).isEqualTo("consumer-1");

        ConsumerGroupDetailsDto.Topic payments = details.getTopics().get(1);
        assertThat(payments.getLag()).isEqualTo(3L);
        assertThat(payments.getMemberCount()).isZero();
        assertThat(payments.getPartitions().get(0).getConsumerId()).isNull();
    }

    @Test
    void lagIsUnknownWithoutACommittedOrEndOffset() {
        MemberDescription member = new MemberDescription("consumer-1", "client-1", "/10.0.0.1",
                new MemberAssignment(Set.of(tp("orders", 0))));
        Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
        committed.put(tp("events", 0), new OffsetAndMetadata(3));   // end offset unreadable

        ConsumerGroupDetailsDto details = ConsumerGroupDetailsCalculator.calculate(
                group(GroupState.STABLE, List.of(member)), committed, Map.of(tp("orders", 0), 50L));

        assertThat(details.getTopics()).extracting(ConsumerGroupDetailsDto.Topic::getName).containsExactly("events", "orders");
        assertThat(details.getTopics()).extracting(ConsumerGroupDetailsDto.Topic::getLag).containsOnlyNulls();
        assertThat(details.getTotalLag()).isNull();
        assertThat(details.getTopics().get(1).getPartitions().get(0).getEndOffset()).isEqualTo(50L);
    }

    @Test
    void emptyGroupHasNoTopics() {
        ConsumerGroupDetailsDto details = ConsumerGroupDetailsCalculator.calculate(
                group(GroupState.EMPTY, List.of()), Map.of(), Map.of());

        assertThat(details.getState()).isEqualTo("Empty");
        assertThat(details.getTopics()).isEmpty();
        assertThat(details.getTopicCount()).isZero();
        assertThat(details.getTotalLag()).isNull();
    }

    private static ConsumerGroupDescription group(GroupState state, List<MemberDescription> members) {
        return new ConsumerGroupDescription("orders-group", false, members, "range", GroupType.CLASSIC, state,
                COORDINATOR, Set.of(), Optional.empty(), Optional.empty());
    }

    private static TopicPartition tp(String topic, int partition) {
        return new TopicPartition(topic, partition);
    }

}
