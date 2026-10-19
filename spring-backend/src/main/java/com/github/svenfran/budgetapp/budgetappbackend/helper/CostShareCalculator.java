package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centgenaue Salden der Ausgabenübersicht (DIV-38, Entscheidung D6): Anteile an Einkäufen werden exakt als Bruch
 * summiert, gerundet wird erst der Saldo – so, dass die Salden einer Periode zusammen exakt 0 ergeben und kein Saldo
 * mehr als einen Cent vom exakten Wert abweicht. Rundungsreste sammeln sich damit nicht über viele Einkäufe an.
 */
public final class CostShareCalculator {

    private CostShareCalculator() {
    }

    /** Betrag in ganze Cent, HALF_UP. {@code BigDecimal.valueOf} vermeidet Binärartefakte wie 0.30000000000000004. */
    public static long toCents(Double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact();
    }

    public static double toEuro(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    /**
     * Gemeinsamer Nenner (kgV) der Teilnehmerzahlen aller Einkäufe. Mit ihm ist jeder Anteil
     * {@code betragInCent / teilnehmer} als ganzzahliger Zähler {@code betragInCent * (nenner / teilnehmer)} darstellbar.
     */
    public static BigInteger commonDenominator(Collection<Integer> participantCounts) {
        var denominator = BigInteger.ONE;
        for (int count : participantCounts) {
            if (count > 0) {
                var value = BigInteger.valueOf(count);
                denominator = denominator.divide(denominator.gcd(value)).multiply(value);
            }
        }
        return denominator;
    }

    /** Exakter Anteil eines Teilnehmers an einem Einkauf als Zähler zum gemeinsamen Nenner. */
    public static BigInteger shareNumerator(long amountCents, int participantCount, BigInteger denominator) {
        return BigInteger.valueOf(amountCents).multiply(denominator.divide(BigInteger.valueOf(participantCount)));
    }

    /**
     * Rundet exakte Salden (Zähler zum gemeinsamen Nenner) nach dem Largest-Remainder-Verfahren auf ganze Cent: alle
     * abrunden, dann die fehlenden Cent an die Salden mit dem größten Rest verteilen. Die Summe der gerundeten Salden
     * ist gleich der exakten Summe (bei vollständig betrachteter Periode 0), jeder Saldo weicht um weniger als einen
     * Cent ab.
     * <p>
     * Gleichstand beim Rest: zuerst Schuldner (negativer Saldo) – so trägt bei einem einzelnen Einkauf der Zahler den
     * Rundungsrest und alle anderen schulden denselben abgerundeten Betrag –, danach die kleinere User-ID.
     *
     * @return Saldo in Cent je User-ID, in der Reihenfolge der Eingabe
     */
    public static Map<Long, Long> roundBalances(Map<Long, BigInteger> exactNumerators, BigInteger denominator) {
        var total = exactNumerators.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        var rounded = new LinkedHashMap<Long, Long>();
        var remainders = new ArrayList<Remainder>();
        var floorSum = BigInteger.ZERO;

        for (var entry : exactNumerators.entrySet()) {
            var division = floorDivMod(entry.getValue(), denominator);
            rounded.put(entry.getKey(), division[0].longValueExact());
            floorSum = floorSum.add(division[0]);
            remainders.add(new Remainder(entry.getKey(), division[1], entry.getValue().signum() < 0));
        }

        // Exakte Summe ist ganzzahlig: Teilnehmeranteile eines Einkaufs ergeben zusammen dessen Betrag.
        long missingCents = floorDiv(total, denominator).subtract(floorSum).longValueExact();
        remainders.sort(Comparator.comparing(Remainder::value).reversed()
                .thenComparing(Remainder::debtor, Comparator.reverseOrder())
                .thenComparing(Remainder::userId));
        for (int i = 0; i < missingCents; i++) {
            rounded.merge(remainders.get(i).userId(), 1L, Long::sum);
        }
        return rounded;
    }

    private static BigInteger[] floorDivMod(BigInteger value, BigInteger divisor) {
        var remainder = value.mod(divisor);
        return new BigInteger[]{value.subtract(remainder).divide(divisor), remainder};
    }

    private static BigInteger floorDiv(BigInteger value, BigInteger divisor) {
        return floorDivMod(value, divisor)[0];
    }

    private record Remainder(Long userId, BigInteger value, boolean debtor) {
    }
}
