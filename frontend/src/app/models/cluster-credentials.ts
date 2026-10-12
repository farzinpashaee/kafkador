/** SASL credentials for a cluster that requires them. Held in server memory for the session only. */
export interface ClusterCredentials {
    securityProtocol: 'SASL_PLAINTEXT' | 'SASL_SSL';
    saslMechanism: 'PLAIN' | 'SCRAM_SHA_256' | 'SCRAM_SHA_512';
    username: string;
    password: string;
}

/** Error code the server answers with when connecting needs (other) credentials. */
export const CREDENTIALS_REQUIRED = 'CREDENTIALS_REQUIRED';

export function blankCredentials(): ClusterCredentials {
    return { securityProtocol: 'SASL_PLAINTEXT', saslMechanism: 'PLAIN', username: '', password: '' };
}
