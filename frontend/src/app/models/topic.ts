import { Config } from './config';
export class Topic {
    id!: string;
    name!: string;
    partitions!: number;
    internal!: boolean;
    replicatorFactor!: number;
    config!: Config[];
}
