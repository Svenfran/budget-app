/**
 * Angewendeter Ausgabenfilter der Ausgaben-Seite (rein clientseitig, kein DTO). `null` bzw. ein fehlendes Feld bedeutet
 * „kein Kriterium“ (DIV-71: das Filter-Sheet liefert immer alle Felder, leere Eingaben als `null`).
 */
export interface CartFilter {
    title?: string | null;
    description?: string | null;
    category?: string[] | null;
    startDate?: Date | null;
    endDate?: Date | null;
    userName?: string[] | null;
}
