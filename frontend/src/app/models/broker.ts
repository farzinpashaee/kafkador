import { Config } from './config';
export class Broker {
    id!: string;
    host!: string;
    port!: string;
    size!: string;
    hash!: string;
    config!: Config[];
}
