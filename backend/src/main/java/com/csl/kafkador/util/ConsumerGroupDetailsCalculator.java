package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.ConsumerGroupDetailsDto;
import com.csl.kafkador.domain.dto.TopicConsumerGroupDto;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Builds the consumer group details page from plain Admin API results, so the lag maths can be tested without a cluster. */
public final class ConsumerGroupDetailsCalculator {

    private static final Comparator<TopicPartition> PARTITION_ORDER =
            Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition);

    private ConsumerGroupDetailsCalculator() {
    }

    /**
     * @param committedOffsets the group's committed offset per partition; a null value means nothing committed
     * @param endOffsets       latest offset per partition; partitions missing here have unknown lag
     */
    public static ConsumerGroupDetailsDto calculate(ConsumerGroupDescription group,
                                                    Map<TopicPartition, OffsetAndMetadata> committedOffsets,
                                                    Map<TopicPartition, Long> endOffsets) {
        Map<TopicPartition, MemberDescription> owners = new HashMap<>();
        group.members().forEach(member ->
                member.assignment().topicPartitions().forEach(tp -> owners.put(tp, member)));

        Set<TopicPartition> partitions = new TreeSet<>(PARTITION_ORDER);
        committedOffsets.keySet().forEach(partitions::add);
        partitions.addAll(owners.keySet());

        // TreeMap + sorted partition set: topics by name, partitions by number.
        Map<String, List<ConsumerGroupDetailsDto.Partition>> byTopic = partitions.stream()
                .collect(Collectors.groupingBy(TopicPartition::topic, TreeMap::new,
                        Collectors.mapping(tp -> partitionRow(tp, committedOffsets.get(tp), endOffsets.get(tp), owners.get(tp)),
                                Collectors.toList())));

        List<ConsumerGroupDetailsDto.Topic> topics = byTopic.entrySet().stream()
                .map(entry -> new ConsumerGroupDetailsDto.Topic()
                        .setName(entry.getKey())
                        .setPartitionCount(entry.getValue().size())
                        .setMemberCount((int) entry.getValue().stream().map(ConsumerGroupDetailsDto.Partition::getConsumerId)
                                .filter(Objects::nonNull).distinct().count())
                        .setLag(sumKnown(entry.getValue().stream().map(ConsumerGroupDetailsDto.Partition::getLag).toList()))
                        .setPartitions(entry.getValue()))
                .toList();

        Node coordinator = group.coordinator();
        return new ConsumerGroupDetailsDto()
                .setId(group.groupId())
                .setState(group.groupState() == null ? null : group.groupState().toString())
                .setType(group.type() == null ? null : group.type().toString())
                .setPartitionAssignor(group.partitionAssignor() == null || group.partitionAssignor().isBlank() ? null : group.partitionAssignor())
                .setSimpleConsumerGroup(group.isSimpleConsumerGroup())
                .setCoordinatorId(coordinator == null || coordinator.isEmpty() ? null : coordinator.id())
                .setCoordinatorHost(coordinator == null || coordinator.isEmpty() ? null : coordinator.host())
                .setMemberCount(group.members().size())
                .setTopicCount(topics.size())
                .setPartitionCount(partitions.size())
                .setTotalLag(sumKnown(topics.stream().map(ConsumerGroupDetailsDto.Topic::getLag).toList()))
                .setTopics(topics);
    }

    /**
     * The groups that consume {@code topic}: those with a committed offset on it or a member assigned one of its
     * partitions, ordered by group id. Other topics the groups read are left out.
     *
     * @param committedOffsets per group id, committed offsets on the topic; null values mean nothing committed
     * @param endOffsets       latest offset per partition of the topic
     */
    public static List<TopicConsumerGroupDto> forTopic(String topic, Collection<ConsumerGroupDescription> groups,
                                                       Map<String, Map<TopicPartition, OffsetAndMetadata>> committedOffsets,
                                                       Map<TopicPartition, Long> endOffsets) {
        return groups.stream()
                .sorted(Comparator.comparing(ConsumerGroupDescription::groupId))
                .map(group -> {
                    Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
                    committedOffsets.getOrDefault(group.groupId(), Map.of()).forEach((tp, offset) -> {
                        if (offset != null && tp.topic().equals(topic)) committed.put(tp, offset);
                    });
                    ConsumerGroupDetailsDto details = calculate(group, committed, endOffsets);
                    return details.getTopics().stream()
                            .filter(t -> t.getName().equals(topic))
                            .findFirst()
                            .map(t -> new TopicConsumerGroupDto()
                                    .setGroupId(details.getId())
                                    .setState(details.getState())
                                    .setType(details.getType())
                                    .setCoordinatorId(details.getCoordinatorId())
                                    .setMemberCount(t.getMemberCount())
                                    .setGroupMemberCount(details.getMemberCount())
                                    .setPartitionCount(t.getPartitionCount())
                                    .setLag(t.getLag()))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static ConsumerGroupDetailsDto.Partition partitionRow(TopicPartition tp, OffsetAndMetadata committed,
                                                                  Long endOffset, MemberDescription owner) {
        Long committedOffset = committed == null ? null : committed.offset();
        Long lag = committedOffset == null || endOffset == null ? null : Math.max(0, endOffset - committedOffset);
        return new ConsumerGroupDetailsDto.Partition()
                .setPartition(tp.partition())
                .setCommittedOffset(committedOffset)
                .setEndOffset(endOffset)
                .setLag(lag)
                .setConsumerId(owner == null ? null : owner.consumerId())
                .setClientId(owner == null ? null : owner.clientId())
                .setHost(owner == null ? null : owner.host());
    }

    /** Sum of the known values, or null when none are known. */
    private static Long sumKnown(List<Long> values) {
        List<Long> known = values.stream().filter(Objects::nonNull).toList();
        return known.isEmpty() ? null : known.stream().mapToLong(Long::longValue).sum();
    }

}
