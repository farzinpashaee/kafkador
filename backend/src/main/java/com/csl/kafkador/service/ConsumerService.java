package com.csl.kafkador.service;

import com.csl.kafkador.domain.ConsumerGroup;
import com.csl.kafkador.domain.dto.ConsumerGroupDetailsDto;
import com.csl.kafkador.domain.dto.OffsetResetRequestDto;
import com.csl.kafkador.domain.dto.OffsetResetResultDto;
import com.csl.kafkador.domain.dto.TopicConsumerGroupDto;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.ConsumerGroupNotFoundException;
import com.csl.kafkador.exception.InvalidOffsetResetException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.TopicNotFoundException;
import com.csl.kafkador.domain.options.OffsetResetTarget;
import com.csl.kafkador.util.ConsumerGroupDetailsCalculator;
import com.csl.kafkador.util.OffsetResetPlanner;
import com.csl.kafkador.util.DtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service("ConsumerService")
@RequiredArgsConstructor
public class ConsumerService {

    private static final long ADMIN_TIMEOUT_SECONDS = 15;

    private final ConnectionService connectionService;

    public Collection<ConsumerGroup> getConsumersGroup(String clusterId) throws KafkaAdminApiException {

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            KafkaFuture<Map<String, ConsumerGroupDescription>> consumerDescribedFuture = admin.describeConsumerGroups(
                    consumersFuture.get().stream().map(i -> i.groupId()).collect(Collectors.toList())).all();

            return consumerDescribedFuture.get().entrySet().stream()
                    .map(i -> DtoMapper.consumerGroupDescriptionMapper(i.getValue()))
                    .collect(Collectors.toList());
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list consumer groups for cluster {}", clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    /** The group's state and members plus, per topic and partition, its committed offsets and lag. */
    public ConsumerGroupDetailsDto getConsumerGroupDetails(String clusterId, String groupId)
            throws KafkaAdminApiException, ConsumerGroupNotFoundException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            ConsumerGroupDescription group = await(admin.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId));
            // Older brokers describe an unknown group as DEAD instead of failing.
            if (group.groupState() == GroupState.DEAD) {
                throw new ConsumerGroupNotFoundException("Consumer group '" + groupId + "' does not exist");
            }
            Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>(
                    await(admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()));

            Set<TopicPartition> partitions = new HashSet<>(committed.keySet());
            group.members().forEach(m -> partitions.addAll(m.assignment().topicPartitions()));
            return ConsumerGroupDetailsCalculator.calculate(group, committed, endOffsets(admin, partitions));
        } catch (ConnectionSessionExpiredException | ConsumerGroupNotFoundException e) {
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GroupIdNotFoundException) {
                throw new ConsumerGroupNotFoundException("Consumer group '" + groupId + "' does not exist");
            }
            log.error("Failed to describe consumer group {} on cluster {}", groupId, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to describe consumer group {} on cluster {}", groupId, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    /**
     * The consumer groups reading {@code topic}: any group with a committed offset on it or a member assigned one of
     * its partitions, with that group's members and lag on this topic only.
     */
    public List<TopicConsumerGroupDto> getTopicConsumerGroups(String clusterId, String topic)
            throws KafkaAdminApiException, TopicNotFoundException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            TopicDescription description = await(admin.describeTopics(List.of(topic)).allTopicNames()).get(topic);
            List<TopicPartition> partitions = description.partitions().stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();

            List<String> groupIds = await(admin.listConsumerGroups().all()).stream()
                    .map(ConsumerGroupListing::groupId)
                    .toList();
            if (groupIds.isEmpty()) return List.of();
            Map<String, ConsumerGroupDescription> groups = await(admin.describeConsumerGroups(groupIds).all());
            // One request for every group's offsets, limited to this topic's partitions.
            ListConsumerGroupOffsetsSpec spec = new ListConsumerGroupOffsetsSpec().topicPartitions(partitions);
            Map<String, Map<TopicPartition, OffsetAndMetadata>> committed = await(admin.listConsumerGroupOffsets(
                    groupIds.stream().collect(Collectors.toMap(Function.identity(), id -> spec))).all());

            return ConsumerGroupDetailsCalculator.forTopic(topic, groups.values(), committed,
                    endOffsets(admin, new HashSet<>(partitions)));
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new TopicNotFoundException("Topic '" + topic + "' does not exist");
            }
            log.error("Failed to list the consumer groups of topic {} on cluster {}", topic, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to list the consumer groups of topic {} on cluster {}", topic, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    /** Latest offset per partition; a partition that can't be read (e.g. deleted topic, no leader) is left out. */
    private Map<TopicPartition, Long> endOffsets(Admin admin, Set<TopicPartition> partitions) {
        if (partitions.isEmpty()) return Map.of();
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        partitions.forEach(tp -> request.put(tp, OffsetSpec.latest()));
        ListOffsetsResult result = admin.listOffsets(request);
        Map<TopicPartition, Long> offsets = new HashMap<>();
        for (TopicPartition tp : partitions) {
            try {
                offsets.put(tp, await(result.partitionResult(tp)).offset());
            } catch (Exception e) {
                log.debug("No end offset for {}: {}", tp, e.getMessage());
            }
        }
        return offsets;
    }

    private static <T> T await(KafkaFuture<T> future) throws Exception {
        return future.get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Moves the group's committed offsets, or with {@code dryRun} only works out where they'd go. Kafka only changes
     * the offsets of a group without active members, so a dry run says why a reset is blocked and applying one fails.
     */
    public OffsetResetResultDto resetOffsets(String clusterId, String groupId, OffsetResetRequestDto request)
            throws KafkaAdminApiException, InvalidOffsetResetException, TopicNotFoundException {
        validate(request);
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            ConsumerGroupDescription group = await(admin.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId));
            Map<TopicPartition, OffsetAndMetadata> committed = group.groupState() == GroupState.DEAD ? Map.of()
                    : await(admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
            Set<TopicPartition> partitions = resetPartitions(admin, group, committed, request);

            Map<TopicPartition, Long> earliest = offsets(admin, partitions, OffsetSpec.earliest());
            Map<TopicPartition, Long> latest = offsets(admin, partitions, OffsetSpec.latest());
            Map<TopicPartition, Long> atTimestamp = request.getTarget() == OffsetResetTarget.TIMESTAMP
                    ? offsets(admin, partitions, OffsetSpec.forTimestamp(request.getTimestamp())) : Map.of();
            Map<TopicPartition, OffsetResetPlanner.PartitionOffsets> current = new HashMap<>();
            for (TopicPartition tp : partitions) {
                OffsetAndMetadata offset = committed.get(tp);
                Long at = atTimestamp.get(tp);
                current.put(tp, new OffsetResetPlanner.PartitionOffsets(offset == null ? null : offset.offset(),
                        earliest.get(tp), latest.get(tp), at == null || at < 0 ? null : at));
            }

            OffsetResetResultDto result = new OffsetResetResultDto()
                    .setChanges(OffsetResetPlanner.plan(request.getTarget(), request.getOffset(), current))
                    .setBlockedReason(group.members().isEmpty() ? null : activeMembers(groupId, group.members().size()));
            if (request.isDryRun()) return result;
            if (result.getBlockedReason() != null) throw new InvalidOffsetResetException(result.getBlockedReason());

            Map<TopicPartition, OffsetAndMetadata> moved = result.getChanges().stream().collect(Collectors.toMap(
                    change -> new TopicPartition(change.getTopic(), change.getPartition()),
                    change -> new OffsetAndMetadata(change.getNewOffset())));
            await(admin.alterConsumerGroupOffsets(groupId, moved).all());
            log.info("Reset {} offsets of consumer group {} on cluster {} to {}", moved.size(), groupId, clusterId, request.getTarget());
            return result.setApplied(true);
        } catch (ConnectionSessionExpiredException | InvalidOffsetResetException | TopicNotFoundException e) {
            throw e;
        } catch (ExecutionException e) {
            // Members can join between the check above and the change; Kafka then refuses it.
            if (e.getCause() instanceof GroupNotEmptyException || e.getCause() instanceof UnknownMemberIdException) {
                throw new InvalidOffsetResetException(activeMembers(groupId, -1));
            }
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new TopicNotFoundException("Topic '" + request.getTopic() + "' does not exist");
            }
            log.error("Failed to reset the offsets of consumer group {} on cluster {}", groupId, clusterId, e);
            throw new KafkaAdminApiException("Error resetting offsets: " + e.getCause().getMessage());
        } catch (Exception e) {
            log.error("Failed to reset the offsets of consumer group {} on cluster {}", groupId, clusterId, e);
            throw new KafkaAdminApiException("Error resetting offsets: " + e.getMessage());
        }
    }

    private static void validate(OffsetResetRequestDto request) throws InvalidOffsetResetException {
        if (request.getTarget() == OffsetResetTarget.TIMESTAMP && request.getTimestamp() == null) {
            throw new InvalidOffsetResetException("Choose the date and time to reset the offsets to.");
        }
        if (request.getTarget() == OffsetResetTarget.OFFSET && request.getOffset() == null) {
            throw new InvalidOffsetResetException("Enter the offset to reset to.");
        }
        if ((request.getTopic() == null || request.getTopic().isBlank()) && !request.getPartitions().isEmpty()) {
            throw new InvalidOffsetResetException("Choose a topic to reset single partitions of.");
        }
    }

    /** The requested partitions of the topic, or every partition the group has offsets on or is assigned. */
    private static Set<TopicPartition> resetPartitions(Admin admin, ConsumerGroupDescription group,
                                                       Map<TopicPartition, OffsetAndMetadata> committed,
                                                       OffsetResetRequestDto request) throws Exception {
        String topic = request.getTopic();
        if (topic == null || topic.isBlank()) {
            Set<TopicPartition> partitions = new HashSet<>(committed.keySet());
            group.members().forEach(m -> partitions.addAll(m.assignment().topicPartitions()));
            if (partitions.isEmpty()) {
                throw new InvalidOffsetResetException("The group has no committed offsets yet; choose a topic to set them on.");
            }
            return partitions;
        }
        TopicDescription description = await(admin.describeTopics(List.of(topic)).allTopicNames()).get(topic);
        Set<Integer> existing = description.partitions().stream().map(p -> p.partition()).collect(Collectors.toSet());
        for (Integer partition : request.getPartitions()) {
            if (!existing.contains(partition)) {
                throw new InvalidOffsetResetException("Topic '" + topic + "' has no partition " + partition + ".");
            }
        }
        return existing.stream()
                .filter(p -> request.getPartitions().isEmpty() || request.getPartitions().contains(p))
                .map(p -> new TopicPartition(topic, p))
                .collect(Collectors.toSet());
    }

    private static Map<TopicPartition, Long> offsets(Admin admin, Set<TopicPartition> partitions, OffsetSpec spec) throws Exception {
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        partitions.forEach(tp -> request.put(tp, spec));
        return await(admin.listOffsets(request).all()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    private static String activeMembers(String groupId, int count) {
        String who = count > 0 ? count + (count == 1 ? " active member" : " active members") : "active members";
        return "Consumer group '" + groupId + "' has " + who + ". Kafka only changes the offsets of a group with none: "
                + "stop its consumers, then reset.";
    }

}
