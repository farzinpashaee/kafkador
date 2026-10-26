/** A consumer group with the topics it consumes. Lag fields are absent when they can't be worked out. */
export interface ConsumerGroupDetails {
    id: string;
    state?: string;
    type?: string;
    partitionAssignor?: string;
    simpleConsumerGroup?: boolean;
    coordinatorId?: number;
    coordinatorHost?: string;
    memberCount: number;
    topicCount: number;
    partitionCount: number;
    totalLag?: number;
    topics: ConsumerGroupTopic[];
}

export interface ConsumerGroupTopic {
    name: string;
    partitionCount: number;
    memberCount: number;
    lag?: number;
    partitions: ConsumerGroupPartition[];
}

export interface ConsumerGroupPartition {
    partition: number;
    committedOffset?: number;
    endOffset?: number;
    lag?: number;
    consumerId?: string;
    clientId?: string;
    host?: string;
}

/** Where a consumer group's offsets are moved to, as with kafka-consumer-groups --reset-offsets. */
export type OffsetResetTarget = 'EARLIEST' | 'LATEST' | 'TIMESTAMP' | 'OFFSET';

export interface OffsetResetRequest {
    /** The topic to reset; none resets every topic the group has offsets on. */
    topic?: string;
    /** Partitions of the topic; none means all of them. */
    partitions: number[];
    target: OffsetResetTarget;
    /** TIMESTAMP: epoch milliseconds. */
    timestamp?: number;
    /** OFFSET: the offset to move to. */
    offset?: number;
    /** Only work out the new offsets. */
    dryRun: boolean;
}

export interface OffsetResetChange {
    topic: string;
    partition: number;
    currentOffset?: number | null;
    newOffset: number;
    endOffset: number;
    lagAfter: number;
    note?: string | null;
}

export interface OffsetResetResult {
    applied: boolean;
    /** Why it can't be applied now, e.g. the group has active members. */
    blockedReason?: string | null;
    changes: OffsetResetChange[];
}
