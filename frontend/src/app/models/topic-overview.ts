export class TopicOverview {
    name!: string;
    id?: string;
    internal!: boolean;
    partitionCount!: number;
    replicationFactor!: number;
    outOfSyncReplicaCount!: number;
    messageCount?: number;
    sizeBytes?: number;
    cleanupPolicy?: string;
}
