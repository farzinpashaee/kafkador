export type AuthenticationMethod = 'NONE' | 'SASL_PLAIN' | 'SASL_SCRAM_SHA_256' | 'SASL_SCRAM_SHA_512';

/** Settings > Security of the active cluster. Passwords are write-only: the server only says whether one is saved. */
export interface ClusterSecurityConfig {
    tls: boolean;
    authenticationMethod: AuthenticationMethod;
    username?: string;
    /** Sent only to change it; blank keeps the saved one. */
    password?: string;
    forgetPassword?: boolean;
    // Read-only
    passwordSaved?: boolean;
    truststoreFileName?: string | null;
    truststoreType?: string | null;
}
