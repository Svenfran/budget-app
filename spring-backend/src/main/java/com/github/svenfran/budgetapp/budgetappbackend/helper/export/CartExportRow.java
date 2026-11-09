package com.github.svenfran.budgetapp.budgetappbackend.helper.export;

import java.time.LocalDate;

/**
 * Eine Zeile des Ausgaben-Blatts bzw. der Ausgaben-CSV (DIV-68), fertig aufbereitet: Benutzername bereits übersetzt
 * (gelöschter Nutzer), Betrag als gespeicherter Rohwert.
 */
public record CartExportRow(String userName, String title, String description, LocalDate datePurchased,
                            double amount, String category, String groupName) {
}
