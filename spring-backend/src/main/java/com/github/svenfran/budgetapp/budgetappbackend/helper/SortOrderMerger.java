package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Neue Reihenfolge der Einkaufslisten bzw. Einträge aus der vom Client gewünschten und der gespeicherten Reihenfolge
 * (DIV-1, Entscheidung E2 „tolerant übernehmen“): Ein veralteter Client-Stand führt zu keinem Fehler.
 */
public final class SortOrderMerger {

    private SortOrderMerger() {
    }

    /**
     * Gewünschte IDs in ihrer Reihenfolge, soweit sie in {@code current} vorkommen (fremde oder gelöschte IDs werden
     * ignoriert, Duplikate zählen beim ersten Vorkommen). Gespeicherte IDs, die im Request fehlen, folgen in ihrer
     * bisherigen Reihenfolge.
     */
    public static List<Long> merge(List<Long> requested, List<Long> current) {
        var known = new HashSet<>(current);
        var merged = new LinkedHashSet<Long>();
        if (requested != null) {
            requested.stream().filter(known::contains).forEach(merged::add);
        }
        merged.addAll(current);
        return new ArrayList<>(merged);
    }
}
