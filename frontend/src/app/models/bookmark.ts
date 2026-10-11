export type BookmarkEntityType = 'TOPIC' | 'BROKER' | 'CONSUMER_GROUP';

export interface Bookmark {
    entityType: BookmarkEntityType;
    entityId: string;
}
