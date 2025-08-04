package com.csl.kafkador.dto;

import lombok.Data;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.Uuid;

import java.util.Collection;
import java.util.List;

@Data
public class Topic {

    private String id;
    private String name;
    private Integer partitions;
    private Short replicatorFactor;
    private List<TopicPartitionInfo> partitionDetails;

}
