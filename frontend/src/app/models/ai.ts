export class AiConfig {
    provider!: string;
    model?: string;
    baseUrl?: string;
    /** Write-only: the server never sends the key back. */
    apiKey?: string;
    enabled!: boolean;
    apiKeySet?: boolean;
    available?: boolean;
}

export class AiMessage {
    role!: 'user' | 'assistant';
    content!: string;
}

export class AiChatResponse {
    reply!: string;
}
