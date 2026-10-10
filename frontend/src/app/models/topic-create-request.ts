/** Payload for creating a topic. Optional settings left null use the broker defaults. */
export class TopicCreateRequest {
    name!: string;
    partitions!: number | null;
    replicatorFactor!: number | null;
    cleanupPolicy!: 'delete' | 'compact' | 'compact,delete';
    minInSyncReplicas!: number | null;
    retentionMs!: number | null;
    retentionBytes!: number | null;
    maxMessageBytes!: number | null;
    configs!: { [name: string]: string };
}
