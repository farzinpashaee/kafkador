import { Broker } from './broker';

export class Cluster {
    id!: string;
    port!: string;
    controller!: Broker;
    brokers!: Broker[];
}
