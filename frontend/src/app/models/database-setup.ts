export class DatabaseSetupStatus {
    databaseSetupRequired!: boolean;
    /** Setup asks for the one-time token Kafkador prints to its log at startup. */
    setupTokenRequired?: boolean;
}

export class DatabaseCredentials {
    username!: string;
    password!: string;
    /** The one-time token from the log, when setup asks for one. */
    setupToken?: string;
}
