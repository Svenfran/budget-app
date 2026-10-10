package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import org.testcontainers.containers.PostgreSQLContainer

/**
 * Einziger Postgres-Container für alle Specs auf Basis von {@link IntegrationSpec}.
 * Wird beim ersten Zugriff einmal pro JVM gestartet; Ryuk räumt ihn nach dem Lauf ab.
 * Bewusst ohne withReuse(true): Reuse würde Daten über Läufe hinweg erhalten.
 */
final class PostgresTestContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:14-alpine")

    static { INSTANCE.start() }

    private PostgresTestContainer() {}
}
