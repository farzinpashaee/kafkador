/** A consumer group as seen from one topic. Lag is absent when it can't be worked out. */
export interface TopicConsumerGroup {
    groupId: string;
    state?: string;
    type?: string;
    coordinatorId?: number;
    /** Members currently assigned partitions of this topic. */
    memberCount: number;
    /** Members of the group overall. */
    groupMemberCount: number;
    /** Partitions of this topic the group has committed offsets on or is assigned. */
    partitionCount: number;
    lag?: number;
}
