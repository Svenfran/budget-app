package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        Set<String> usedSheets = new HashSet<>();
        Set<String> usedFolders = new HashSet<>();
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
                        folderName(folderBase, suffix));
                counter++;
            } while (usedSheets.contains(key(names.cartSheet())) || usedSheets.contains(key(names.membershipSheet()))
                    || usedFolders.contains(key(names.folder())));
            usedSheets.add(key(names.cartSheet()));
            usedSheets.add(key(names.membershipSheet()));
            usedFolders.add(key(names.folder()));
            result.put(group.groupId(), names);
        }
        return result;
    }

    /** {@code <Name><Zähler> <Label>}, auf 31 Zeichen nur der Name gekürzt. */
    private static String sheetName(String base, String suffix, String label) {
        int maxBase = Math.max(1, MAX_SHEET_NAME - suffix.length() - 1 - label.length());
        return truncate(base, maxBase) + suffix + " " + label;
    }

    /** {@code <Name><Zähler>}, auf 40 Zeichen nur der Name gekürzt; kein abschließender Punkt. */
    private static String folderName(String base, String suffix) {
        String name = stripTrailingDots(truncate(base, MAX_FOLDER_NAME - suffix.length()));
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

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
