package com.github.svenfran.budgetapp.budgetappbackend.helper.export;

import java.time.LocalDate;

/**
 * Eine Zeile des Ausgaben-Blatts bzw. der Ausgaben-CSV (DIV-68), fertig aufbereitet: Benutzername bereits übersetzt
 * (gelöschter Nutzer), Betrag als gespeicherter Rohwert, Währung als ISO-4217-Code der Gruppe (DIV-77, Spalte direkt
 * nach dem Betrag; nichts umgerechnet).
 */
public record CartExportRow(String userName, String title, String description, LocalDate datePurchased,
                            double amount, String currencyCode, String category, String groupName) {
}
