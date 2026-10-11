package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * One consumer group with the topics it consumes and how far behind it is on each.
 * Lag values are null where they can't be worked out (no committed offset or no end offset).
 */
@Data
@Accessors(chain = true)
public class ConsumerGroupDetailsDto {

    private String id;
    private String state;
    private String type;
    private String partitionAssignor;
    private Boolean simpleConsumerGroup;
    private Integer coordinatorId;
    private String coordinatorHost;
    private Integer memberCount;
    private Integer topicCount;
    private Integer partitionCount;
    private Long totalLag;
    private List<Topic> topics;

    /** A topic the group has committed offsets on or has partitions assigned from. */
    @Data
    @Accessors(chain = true)
    public static class Topic {
        private String name;
        private Integer partitionCount;
        private Integer memberCount;
        private Long lag;
        private List<Partition> partitions;
    }

    @Data
    @Accessors(chain = true)
    public static class Partition {
        private Integer partition;
        private Long committedOffset;
        private Long endOffset;
        private Long lag;
        // The member currently assigned this partition; null when none is (e.g. the group is empty)
        private String consumerId;
        private String clientId;
        private String host;
    }

}
