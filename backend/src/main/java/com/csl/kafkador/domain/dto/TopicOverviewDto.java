package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** One row of the Topics page. Values the cluster couldn't provide are left null. */
@Data
@Accessors(chain = true)
public class TopicOverviewDto {

    private String name;
    private String id;
    // Kafka's own internal topics plus, by convention, any topic whose name starts with "_" (_schemas, _connect-*)
    private Boolean internal;
    private Integer partitionCount;
    private Integer replicationFactor;
    private Integer outOfSyncReplicaCount;
    // End minus start offset over all partitions; null for compacted topics, where that isn't a message count
    private Long messageCount;
    // Disk space used by the topic across all brokers, i.e. every replica counted
    private Long sizeBytes;
    private String cleanupPolicy;

}
