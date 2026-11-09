package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Blatt- und Ordnernamen des Exports „Alle Gruppen“ (DIV-68), ohne technische ID:
 * {@code Haushalt Ausgaben} / {@code Haushalt Mitglieder} / Ordner {@code Haushalt}. Bei Namensgleichheit (case-
 * insensitiv, nach Bereinigung und Kürzung) bekommt die Gruppe einen Zähler {@code Haushalt (2) …}, der für alle drei
 * Namen der Gruppe gilt. XLSX- und CSV-Writer bilden die Namen ausschließlich über {@link #assign}.
 */
public final class ExportNames {

    /** Excel-Grenze für Blattnamen. */
    static final int MAX_SHEET_NAME = 31;
    static final int MAX_FOLDER_NAME = 40;

    public record GroupNames(String cartSheet, String membershipSheet, String folder) {
    }

    private ExportNames() {
    }

    /**
     * Rein und deterministisch: Gruppen in der übergebenen Reihenfolge (nach ID sortiert), der Zähler hängt damit an der
     * Reihenfolge. Der Zähler wird pro Gruppe so gewählt, dass beide Blattnamen und der Ordnername noch frei sind.
     *
     * @return Namen je Gruppen-ID, in der Reihenfolge der Gruppen
     */
    public static Map<Long, GroupNames> assign(List<GroupExport> groups, String cartLabel, String membershipLabel,
                                               String fallback) {
        // Gleichheit wie Apache POI beim Anlegen eines Blatts (String.equalsIgnoreCase); toLowerCase(Locale.ROOT)
        // wäre bei Unicode nicht gleichwertig (z. B. „İstanbul“/„istanbul“). Ordner ebenso, Windows ist case-insensitiv.
        Set<String> usedSheets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> usedFolders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<Long, GroupNames> result = new LinkedHashMap<>();
        for (GroupExport group : groups) {
            String sheetBase = orFallback(sanitizeSheetName(group.groupName()), fallback);
            String folderBase = orFallback(sanitizeFolderName(group.groupName()), fallback);
            GroupNames names;
            int counter = 1;
            do {
                String suffix = counter == 1 ? "" : " (" + counter + ")";
                names = new GroupNames(
                        sheetName(sheetBase, suffix, cartLabel),
                        sheetName(sheetBase, suffix, membershipLabel),
                        folderName(folderBase, suffix, fallback));
                counter++;
            } while (usedSheets.contains(names.cartSheet()) || usedSheets.contains(names.membershipSheet())
                    || usedFolders.contains(names.folder()));
            usedSheets.add(names.cartSheet());
            usedSheets.add(names.membershipSheet());
            usedFolders.add(names.folder());
            result.put(group.groupId(), names);
        }
        return result;
    }

    /** {@code <Name><Zähler> <Label>}, auf 31 Zeichen nur der Name gekürzt. */
    private static String sheetName(String base, String suffix, String label) {
        int maxBase = Math.max(1, MAX_SHEET_NAME - suffix.length() - 1 - label.length());
        return truncate(base, maxBase) + suffix + " " + label;
    }

    /**
     * {@code <Name><Zähler>}, auf 40 Zeichen nur der Name gekürzt; kein abschließender Punkt. Bleibt nach dem Kürzen
     * nichts übrig (z. B. 40 Punkte + „X“), gilt wie bei einem leeren Namen der Fallback – sonst entstünden ZIP-Einträge
     * ohne Gruppenordner ({@code /ausgaben.csv}). Läuft vor der Kollisionsprüfung, der Zähler greift also auch hier.
     */
    private static String folderName(String base, String suffix, String fallback) {
        int maxBase = MAX_FOLDER_NAME - suffix.length();
        String name = stripTrailingDots(truncate(base, maxBase));
        if (name.isEmpty()) {
            name = stripTrailingDots(truncate(fallback, maxBase));
        }
        return name + suffix;
    }

    /** In Blattnamen verbotene Zeichen {@code \ / ? * [ ] :} → Leerzeichen; kein {@code '} am Rand. */
    static String sanitizeSheetName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = collapse(name.replaceAll("[\\\\/?*\\[\\]:]", " ").replaceAll("\\p{Cntrl}", " "));
        cleaned = cleaned.replaceAll("^'+|'+$", "");
        return cleaned.trim();
    }

    /** Nur Buchstaben, Ziffern, Leerzeichen und {@code . _ -}; kein abschließender Punkt (Windows). */
    static String sanitizeFolderName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        name.codePoints().forEach(cp -> {
            boolean allowed = Character.isLetterOrDigit(cp) || cp == ' ' || cp == '.' || cp == '_' || cp == '-';
            builder.appendCodePoint(allowed ? cp : ' ');
        });
        return stripTrailingDots(collapse(builder.toString()));
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        // kein halbes Surrogatpaar (Emoji) am Ende
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end).trim();
    }

    private static String stripTrailingDots(String value) {
        return value.replaceAll("[.\\s]+$", "");
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String orFallback(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }
}
