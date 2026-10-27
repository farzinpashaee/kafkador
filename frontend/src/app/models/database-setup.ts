export class DatabaseSetupStatus {
    databaseSetupRequired!: boolean;
    /** Who may use Kafkador hasn't been chosen yet. */
    accessSetupRequired?: boolean;
    /** Setup asks for the one-time token Kafkador prints to its log at startup. */
    setupTokenRequired?: boolean;
}

export class DatabaseCredentials {
    username!: string;
    password!: string;
    /** The one-time token from the log, when setup asks for one. */
    setupToken?: string;
}
