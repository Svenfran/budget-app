package com.github.svenfran.budgetapp.budgetappbackend.constants;

/**
 * Zähler-Scopes des Login-Rate-Limits (DIV-64).
 *
 * <p><b>Die Reihenfolge der Konstanten ist die verbindliche Sperrreihenfolge</b> ({@code values()} = IP → ACCOUNT →
 * ACCOUNT_IP): Jede Operation, die mehrere Scopes in einer Transaktion bearbeitet (Reservieren und Freigeben),
 * iteriert über {@code values()}. So können sich zwei solche Transaktionen auf denselben Zeilen nur gegenseitig
 * warten lassen, aber keinen Deadlock bilden. Nicht umsortieren.
 */
public enum LoginAttemptScope {
    /** Client-IP über alle Accounts (IPv6 auf /64 gekürzt). */
    IP,
    /** Normalisierte E-Mail über alle IPs. */
    ACCOUNT,
    /** Normalisierte E-Mail + Client-IP. */
    ACCOUNT_IP
}
