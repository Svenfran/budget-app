export interface GroupOverview {
    id: number;
    name: string;
    dateCreated: Date;
    ownerName: string;
    /** User-ID des Gruppenadmins: Admin-Erkennung per ID statt Name (DIV-80). */
    ownerId: number;
    memberCount: number;
    /** Gruppenwährung (ISO 4217, DIV-77). */
    currencyCode: string;
    /**
     * Hat die Gruppe Buchungen (Carts)? Steuert den Bestätigungsdialog beim Währungswechsel (DIV-77). Fehlt bei Gruppen,
     * die per Live-Event in die Übersicht kamen: `undefined` = unbekannt, wird wie „Buchungen möglich“ behandelt.
     */
    hasBookings?: boolean;
}