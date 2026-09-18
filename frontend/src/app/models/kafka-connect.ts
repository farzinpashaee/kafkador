export class KafkaConnectConfig {
    configured!: boolean;
    url?: string;
}

export class ConnectorPlugin {
    className!: string;
    type!: string;
    version?: string;
}

export class ConnectorTask {
    id!: number;
    state!: string;
    workerId?: string;
    trace?: string;
}

export class Connector {
    name!: string;
    type?: string;
    state?: string;
    workerId?: string;
    trace?: string;
    tasks!: ConnectorTask[];
    config!: { [key: string]: string };
}

export class ConnectorCreateRequest {
    name!: string;
    config!: { [key: string]: string };
}
