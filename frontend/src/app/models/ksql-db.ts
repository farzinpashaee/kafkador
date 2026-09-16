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
