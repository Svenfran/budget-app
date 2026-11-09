package com.github.svenfran.budgetapp.budgetappbackend.helper.export;

import java.time.LocalDate;

/**
 * Eine Zeile der Mitgliederhistorie (DIV-68). {@code end} ist {@code null} bei offener Mitgliedschaft, {@code type}
 * der Rohwert von TypeEnum (OWNER/MEMBER) wie im bisherigen XLSX.
 */
public record MembershipExportRow(String userName, LocalDate start, LocalDate end, String type) {
}
