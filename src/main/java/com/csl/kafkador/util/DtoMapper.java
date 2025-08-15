package com.csl.kafkador.util;

import com.csl.kafkador.dto.Broker;
import com.csl.kafkador.dto.ConsumerGroup;
import com.csl.kafkador.dto.Topic;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.Node;

public class DtoMapper {

    public static Broker clusterNodeMapper(Node node){
        return new Broker().setId(node.id())
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
                .setIsInternal(topicDescription.isInternal());
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
