export class Connection {
    id!: string;
    clusterId!: string;
    host!: string;
    port!: string;
    name!: string;
    defaultConnection?: boolean;
    redirectAfterLogin?: boolean;
    agentEnabled?: boolean;
}
