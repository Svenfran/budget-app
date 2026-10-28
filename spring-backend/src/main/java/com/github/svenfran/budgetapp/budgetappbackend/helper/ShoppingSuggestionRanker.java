package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingPurchase;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Einkaufsvorschläge einer Liste (DIV-23), einfach und deterministisch; austauschbar, ohne die Historie zu ändern.
 * Namen werden über {@link ShoppingItemNames#normalize} verglichen; Namen, die bereits auf der Liste stehen (offen oder
 * erledigt), werden nicht vorgeschlagen.
 */
public final class ShoppingSuggestionRanker {

    private static final Comparator<Candidate> RANKING = Comparator
            .comparingInt((Candidate c) -> c.count).reversed()
            .thenComparing((Candidate c) -> c.lastPurchasedAt, Comparator.reverseOrder())
            .thenComparing(c -> c.key);

    private ShoppingSuggestionRanker() {
    }

    /**
     * Vorschläge aus den Käufen: sortiert nach Anzahl der Käufe, dann nach dem letzten Kauf (jeweils absteigend), dann
     * nach Name. Angezeigt wird die Schreibweise des jüngsten Kaufs (bei gleichem Zeitpunkt die höhere Ereignis-ID).
     * Käufe ohne Namen werden ignoriert.
     */
    public static List<String> rank(Collection<ShoppingPurchase> purchases, Collection<String> existingNames, int limit) {
        var excluded = normalizedNames(existingNames);
        var candidates = new HashMap<String, Candidate>();
        for (var purchase : purchases) {
            var key = ShoppingItemNames.normalize(purchase.getItemName());
            if (key != null && !excluded.contains(key)) {
                candidates.computeIfAbsent(key, Candidate::new).add(purchase);
            }
        }
        return candidates.values().stream()
                .sorted(RANKING)
                .limit(Math.max(limit, 0))
                .map(c -> c.displayName)
                .toList();
    }

    /** Standardvorschläge in ihrer Reihenfolge, ohne Namen der Liste und ohne Duplikate. */
    public static List<String> filterDefaults(List<String> defaults, Collection<String> existingNames, int limit) {
        var excluded = normalizedNames(existingNames);
        var seen = new HashSet<String>();
        return defaults.stream()
                .filter(name -> {
                    var key = ShoppingItemNames.normalize(name);
                    return key != null && !excluded.contains(key) && seen.add(key);
                })
                .map(String::trim)
                .limit(Math.max(limit, 0))
                .toList();
    }

    private static Set<String> normalizedNames(Collection<String> names) {
        return names.stream()
                .map(ShoppingItemNames::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static final class Candidate {

        private final String key;
        private int count;
        private Date lastPurchasedAt;
        private Long lastId;
        private String displayName;

        private Candidate(String key) {
            this.key = key;
        }

        private void add(ShoppingPurchase purchase) {
            count++;
            if (displayName == null || isNewer(purchase)) {
                lastPurchasedAt = purchase.getPurchasedAt();
                lastId = purchase.getId();
                displayName = purchase.getItemName().trim();
            }
        }

        private boolean isNewer(ShoppingPurchase purchase) {
            var compared = purchase.getPurchasedAt().compareTo(lastPurchasedAt);
            return compared > 0 || compared == 0 && purchase.getId() > lastId;
        }
    }
}
