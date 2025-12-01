import { Broker } from './broker';

export class Cluster {
    id!: string;
    clusterId!: string;
    port!: string;
    host!: string;
    name!: string;
    controller!: Broker;
    brokers!: Broker[];
}
