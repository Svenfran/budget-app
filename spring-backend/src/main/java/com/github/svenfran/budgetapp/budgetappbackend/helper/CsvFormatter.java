package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV-Formatierung des Ausgabenexports (DIV-68): Trennzeichen {@code ;}, Zeilenende CRLF, Quoting nach RFC 4180,
 * Datum ISO {@code yyyy-MM-dd}, Beträge mit Dezimalpunkt ungerundet (mindestens zwei Nachkommastellen).
 */
public final class CsvFormatter {

    static final String SEPARATOR = ";";
    static final String LINE_END = "\r\n";

    private CsvFormatter() {
    }

    /** Eine Zeile inkl. CRLF; Felder werden bei Bedarf in Anführungszeichen gesetzt. */
    public static String line(List<String> fields) {
        return fields.stream().map(CsvFormatter::quote).collect(Collectors.joining(SEPARATOR)) + LINE_END;
    }

    /**
     * Freitext (Titel, Namen, Kategorie …). Werte, die Excel/Sheets als Formel deuten könnten ({@code = + - @}, Tab, CR
     * am Anfang), bekommen ein {@code '} vorangestellt (Schutz vor Formula-Injection).
     */
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /** Gespeicherter Betrag ohne Rundung, nur auf zwei Nachkommastellen aufgefüllt, nie in Exponentialschreibweise. */
    public static String amount(double value) {
        var decimal = BigDecimal.valueOf(value);
        if (decimal.scale() < 2) {
            decimal = decimal.setScale(2);
        }
        return decimal.toPlainString();
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String quote(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.contains(SEPARATOR) || field.contains("\"") || field.contains("\r")
                || field.contains("\n") || field.startsWith(" ") || field.endsWith(" ");
        return needsQuotes ? "\"" + field.replace("\"", "\"\"") + "\"" : field;
    }
}
