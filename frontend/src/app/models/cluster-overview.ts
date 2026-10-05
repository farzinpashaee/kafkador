export class BrokerOverview {
    id!: string;
    host!: string;
    port!: number;
    rack?: string;
    activeController!: boolean;
    diskUsageBytes?: number;
    logCount?: number;
    replicaCount!: number;
    inSyncReplicaCount!: number;
    leaderCount!: number;
    replicasSkew?: number;
    leadersSkew?: number;
}

export class ClusterOverview {
    brokerCount!: number;
    activeControllerId?: string;
    version?: string;
    controllerType?: string;
    partitionCount!: number;
    onlinePartitionCount!: number;
    underReplicatedPartitionCount!: number;
    replicaCount!: number;
    inSyncReplicaCount!: number;
    outOfSyncReplicaCount!: number;
    brokers!: BrokerOverview[];
}
