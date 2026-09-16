import { Schema } from './schema';
export class SchemaRegistry {
    configured!: boolean;
    subjects!: Schema[];
}

export class SchemaRegistryConfig {
    configured!: boolean;
    url?: string;
}
