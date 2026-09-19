import { Schema } from './schema';
export class SchemaRegistry {
    configured!: boolean;
    subjects!: Schema[];
}

export class SchemaRegistryConfig {
    configured!: boolean;
    url?: string;
}

export class SchemaVersion {
    subject!: string;
    version!: number;
    id!: number;
    schemaType?: string;
    schema!: string;
}

export class SchemaRegisterRequest {
    schema!: string;
    schemaType?: string;
}

export class CompatibilityCheckResult {
    compatible!: boolean;
    messages?: string[];
}

export class CompatibilityConfig {
    level?: string;
}

export class SchemaLocation {
    subject!: string;
    version!: number;
}

export class SchemaLookupResult {
    id!: number;
    schemaType?: string;
    schema!: string;
    locations!: SchemaLocation[];
}
