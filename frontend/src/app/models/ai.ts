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
    sessionId!: string;
    reply!: string;
}

export class AiChatSession {
    id!: string;
    /** The first message the user sent in the session. */
    title!: string;
    createDateTime!: string;
    updateDateTime!: string;
    /** Only present when a single session is loaded. */
    messages?: AiMessage[];
}
