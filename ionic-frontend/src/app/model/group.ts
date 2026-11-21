export interface Group {
    id: number,
    name: string,
    dateCreated: Date,
    flag?: string,
    /**
     * Gruppenwährung (ISO 4217, DIV-77). Optional, weil eine vor DIV-77 gespeicherte aktive Gruppe (`ACTIVE_GROUP`)
     * keine hat; gelesen wird sie über `GroupService.activeCurrency` (Rückfall EUR).
     */
    currencyCode?: string,
    /**
     * Nur Request (`PUT /api/groups/update`, DIV-77): ausdrückliche Bestätigung eines Währungswechsels bei vorhandenen
     * Buchungen; ohne sie antwortet der Server bei Buchungen mit 409.
     */
    confirmCurrencyChange?: boolean,
}
