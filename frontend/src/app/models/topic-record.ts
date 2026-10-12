export type RecordSerde = 'STRING' | 'JSON' | 'INT' | 'LONG' | 'FLOAT' | 'DOUBLE' | 'BASE64' | 'HEX';
export type RecordSeekType = 'NEWEST' | 'OLDEST' | 'OFFSET' | 'TIMESTAMP';

export interface RecordFilter {
    field: 'KEY' | 'VALUE' | 'HEADERS' | 'ANY';
    operator: 'CONTAINS' | 'NOT_CONTAINS' | 'EQUALS';
    value: string;
}

export interface TopicRecordSearchRequest {
    seekType: RecordSeekType;
    /** Empty means all partitions. */
    partitions: number[];
    offset?: number | null;
    /** Epoch milliseconds. */
    timestamp?: number | null;
    limit: number;
    keySerde: RecordSerde;
    valueSerde: RecordSerde;
    filters: RecordFilter[];
}

export interface TopicRecord {
    partition: number;
    offset: number;
    timestamp: number;
    timestampType?: string;
    key?: string | null;
    value?: string | null;
    /** -1 for a null key / value. */
    keySize: number;
    valueSize: number;
    headers: { key: string; value?: string | null }[];
}

export interface TopicRecordSearchResult {
    records: TopicRecord[];
    scannedCount: number;
    scanLimitReached: boolean;
}

export interface TopicRecordProduceRequest {
    /** Null lets the producer pick the partition. */
    partition: number | null;
    key: string | null;
    value: string | null;
    keySerde: RecordSerde;
    valueSerde: RecordSerde;
    headers: Record<string, string>;
}

export interface TopicRecordProduceResult {
    partition: number;
    offset: number;
    timestamp: number;
}
