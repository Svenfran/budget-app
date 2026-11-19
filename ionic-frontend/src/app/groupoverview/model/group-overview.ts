export interface GroupOverview {
    id: number;
    name: string;
    dateCreated: Date;
    ownerName: string;
    /** User-ID des Gruppenadmins: Admin-Erkennung per ID statt Name (DIV-80). */
    ownerId: number;
    memberCount: number;
}