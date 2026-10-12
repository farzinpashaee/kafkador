package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** A consumer group as seen from one topic: how many of its members read it and how far behind it is there. */
@Data
@Accessors(chain = true)
public class TopicConsumerGroupDto {

    private String groupId;
    private String state;
    private String type;
    private Integer coordinatorId;
    /** Members currently assigned partitions of this topic. */
    private Integer memberCount;
    /** Members of the group overall, this topic or not. */
    private Integer groupMemberCount;
    /** Partitions of this topic the group has committed offsets on or is assigned. */
    private Integer partitionCount;
    /** Lag summed over those partitions; null when it can't be worked out for any of them. */
    private Long lag;

}
