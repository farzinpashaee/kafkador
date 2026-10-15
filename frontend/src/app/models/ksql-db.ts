export class KsqlDbConfig {
    configured!: boolean;
    url?: string;
}

export class KsqlServerInfo {
    version!: string;
    kafkaClusterId!: string;
    ksqlServiceId!: string;
    serverStatus!: string;
}

export class KsqlStream {
    name!: string;
    topic!: string;
    keyFormat?: string;
    valueFormat?: string;
}

export class KsqlTable {
    name!: string;
    topic!: string;
    keyFormat?: string;
    valueFormat?: string;
}

export class KsqlQuery {
    id!: string;
    queryType!: string;
    status?: string;
    sinks?: string[];
    sources?: string[];
}

/** Which list a stream or table endpoint is about, as it appears in the URL. */
export type KsqlSourceKind = 'streams' | 'tables';

/** One statement creating or changing a stream or table. */
export interface KsqlStatementRequest {
    statement: string;
    /** For CREATE ... AS SELECT: process the source's existing records too, not only new ones. */
    fromEarliest?: boolean;
}

/** What ksqlDB said about a statement it accepted, such as "Stream created". */
export interface KsqlStatementResult {
    status?: string;
    message?: string;
    queryId?: string;
}

/** A stream or table as ksqlDB describes it. */
export interface KsqlSourceDefinition {
    name: string;
    type?: string;
    topic?: string;
    keyFormat?: string;
    valueFormat?: string;
    statement?: string;
    readQueries: string[];
    writeQueries: string[];
}
