/** Who may use Kafkador: nobody signs in, the admin account in Kafkador's database, or LDAP accounts. */
export type AccessMode = 'NONE' | 'DATABASE' | 'LDAP';

export class AccessStatus {
    /** Null until first-run setup has chosen. */
    mode?: AccessMode | null;
    loginRequired!: boolean;
    authenticated!: boolean;
    username?: string;
}

export class LdapSettings {
    url = '';
    baseDn = '';
    userDnPattern = '';
    userSearchBase = '';
    userSearchFilter = '';
    managerDn = '';
    /** Blank keeps the saved one. */
    managerPassword = '';
    managerPasswordSaved?: boolean;
}

export class AccessSettings {
    mode?: AccessMode;
    /** DATABASE: the admin account. A blank password keeps the current one when the username stays the same. */
    username = '';
    password = '';
    ldap: LdapSettings = new LdapSettings();
    /** LDAP: an account that signs in with the new settings before they're saved. */
    testUsername = '';
    testPassword = '';
    /** KAFKADOR_ACCESS_MODE decides, so Settings can't change it. */
    managedByEnvironment?: boolean;
    /** The one-time token from the log, during first-run setup. */
    setupToken?: string;
}

export class LoginRequest {
    username!: string;
    password!: string;
}
