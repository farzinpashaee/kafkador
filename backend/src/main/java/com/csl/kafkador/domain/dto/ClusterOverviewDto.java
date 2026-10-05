package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Health summary of a cluster plus per-broker replica/leader distribution, for the Cluster page.
 * Fields that the cluster doesn't expose (or the connection isn't allowed to read) are left null.
 */
@Data
@Accessors(chain = true)
public class ClusterOverviewDto {

    private Integer brokerCount;
    private String activeControllerId;
    private String version;
    // "KRaft" or "ZooKeeper"
    private String controllerType;

    private Integer partitionCount;
    private Integer onlinePartitionCount;
    private Integer underReplicatedPartitionCount;
    private Integer replicaCount;
    private Integer inSyncReplicaCount;
    private Integer outOfSyncReplicaCount;

    private List<BrokerOverviewDto> brokers;

}
