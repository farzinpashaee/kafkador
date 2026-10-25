/** How Kafkador signs in to Schema Registry, Kafka Connect or ksqlDB. */
export type ServiceAuthMethod = 'NONE' | 'BASIC' | 'BEARER';

/**
 * Sign-in and TLS settings of one of those services. Secrets (password, token, CA certificate) are only sent: reads
 * say whether one is saved, and leaving one blank on save keeps it.
 */
export interface ServiceAuth {
    method: ServiceAuthMethod;
    /** BASIC: the username, or the API key. */
    username?: string;
    password?: string;
    passwordSaved?: boolean;
    token?: string;
    tokenSaved?: boolean;
    /** PEM CA certificate(s) to trust instead of the public CAs. */
    caCertificate?: string;
    clearCaCertificate?: boolean;
    /** Who the saved CA certificate belongs to. */
    caCertificateSubject?: string;
}

export function blankServiceAuth(): ServiceAuth {
    return { method: 'NONE' };
}

/** A service config as loaded, with sign-in settings even from a server that doesn't send them. */
export function withAuth<T extends { configured: boolean; url?: string; auth?: ServiceAuth }>(config?: T | null): T & { auth: ServiceAuth } {
    const value = config ?? ({ configured: false, url: '' } as T);
    return { ...value, auth: value.auth ?? blankServiceAuth() };
}
