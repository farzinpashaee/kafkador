package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BrokerOverviewDto {

    private String id;
    private String host;
    private Integer port;
    private String rack;
    private Boolean activeController;

    // Size of all partition logs on the broker's log dirs, and how many partition logs that is. Null when the
    // log dirs couldn't be described (e.g. missing DESCRIBE permission on the cluster).
    private Long diskUsageBytes;
    private Integer logCount;

    private Integer replicaCount;
    private Integer inSyncReplicaCount;
    private Integer leaderCount;

    // Percent above (+) or below (-) the even share across brokers; null when the cluster has none at all.
    private Double replicasSkew;
    private Double leadersSkew;

}
