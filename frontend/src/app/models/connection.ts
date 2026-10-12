import { ClusterCredentials } from './cluster-credentials';

export class Connection {
    id!: string;
    clusterId!: string;
    host!: string;
    port!: string;
    name!: string;
    defaultConnection?: boolean;
    redirectAfterLogin?: boolean;
    agentEnabled?: boolean;
    /** Whether this session connected with credentials. */
    authenticated?: boolean;
    /** Sent with add / edit for a cluster that requires credentials; never returned by the server. */
    credentials?: ClusterCredentials;
}
