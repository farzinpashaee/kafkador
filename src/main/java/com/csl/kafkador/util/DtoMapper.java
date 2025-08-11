package com.csl.kafkador.util;

import com.csl.kafkador.dto.Broker;
import com.csl.kafkador.dto.Topic;
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
}
