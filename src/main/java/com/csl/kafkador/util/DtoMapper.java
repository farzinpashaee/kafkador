package com.csl.kafkador.util;

import com.csl.kafkador.dto.Node;
import com.csl.kafkador.dto.ConsumerGroup;
import com.csl.kafkador.dto.Partition;
import com.csl.kafkador.dto.Topic;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.stream.Collectors;

public class DtoMapper {

    public static Node clusterNodeMapper(org.apache.kafka.common.Node node){
        return new Node().setId(node.id())
                .setHost(node.host())
                .setPort(node.port())
                .setIdString(node.idString())
                .setRack(node.rack())
                .setHash(node.hashCode())
                .setIsFenced(node.isFenced());
    }

    public static Topic topicMapper(TopicListing topicListing){
        return new Topic().setName(topicListing.name())
                .setId(topicListing.topicId().toString())
                .setIsInternal(topicListing.isInternal());
    }

    public static Topic topicDescriptionMapper(TopicDescription topicDescription){
        return new Topic().setName(topicDescription.name())
                .setId(topicDescription.topicId().toString())
                .setPartitions(topicDescription.partitions().size())
                .setPartitionDetails( topicDescription.partitions().stream().map(DtoMapper::topicPartitionInfoMapper).collect(Collectors.toList()) )
                .setIsInternal(topicDescription.isInternal());
    }


    public static Partition topicPartitionInfoMapper(TopicPartitionInfo topicPartitionInfo){
        return new Partition()
                .setPartition(topicPartitionInfo.partition())
                .setLeader(nodeMapper(topicPartitionInfo.leader()))
                .setReplicas(topicPartitionInfo.replicas().stream().map(DtoMapper::nodeMapper).collect(Collectors.toList()))
                .setIsr(topicPartitionInfo.isr().stream().map(DtoMapper::nodeMapper).collect(Collectors.toList()));
    }

    public static Node nodeMapper( org.apache.kafka.common.Node node ){
        return new Node()
                .setPort(node.port())
                .setHost(node.host())
                .setIsFenced(node.isFenced())
                .setIdString(node.idString())
                .setRack(node.rack());
    }


    public static ConsumerGroup consumerGroupMapper(ConsumerGroupListing consumerGroupListing){
        return new ConsumerGroup().setId(consumerGroupListing.groupId());
    }


    public static ConsumerGroup consumerGroupDescriptionMapper(ConsumerGroupDescription consumerGroupDescription){
        return new ConsumerGroup().setId(consumerGroupDescription.groupId())
                .setSimpleConsumerGroup(consumerGroupDescription.isSimpleConsumerGroup())
                .setGroupState(consumerGroupDescription.groupState() + "")
                .setCoordinator(consumerGroupDescription.coordinator().host())
                .setType(consumerGroupDescription.type() + "");
    }
}
