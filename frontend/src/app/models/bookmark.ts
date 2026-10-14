export type BookmarkEntityType = 'TOPIC' | 'BROKER' | 'CONSUMER_GROUP' | 'KSQL_STREAM' | 'KSQL_TABLE' | 'KSQL_QUERY';

export interface Bookmark {
    entityType: BookmarkEntityType;
    entityId: string;
}
