export class Connection {
    id!: string;
    host!: string;
    port!: string;
    name!: string;
    defaultConnection?: boolean;
    redirectAfterLogin?: boolean;
}
